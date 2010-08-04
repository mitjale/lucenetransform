/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.store.transform;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.transform.algorithm.ReadDataTransformer;

/** Transparent file read transformation. Since file has log based structure, seek and
 * write are just appended, chunks are merged on read request. Chunk can be overwritten
 * multiple times. Chunks are sorted by position when opening file, to improve seek/read
 * performance. Chunk directory is loaded into memory.
 *
 * @author Mitja Lenič
 */
public class TransformedIndexInput extends IndexInput {

    /** allocation is key performance problem, therefore every malloc we can
     *  avoid is ok. MemoryCache is shared instance between original and all clones.
     */
    private static class MemoryCache {

        /** make short list of last allocations */
        private SharedBuffer[] buffers = new SharedBuffer[10];

        synchronized SharedBuffer newBuffer(int size) {        
            for (int i = 0; i < buffers.length; i++) {
                if (buffers[i] != null && buffers[i].data.length >= size) {
                    SharedBuffer result = buffers[i];
                    buffers[i] = null;
                    return result;
                }
            }
            return new SharedBuffer(size);
        }

        synchronized void release(SharedBuffer buffer) {
            buffer.refCount--;
            if (buffer.refCount == 0) {
                for (int i = 0; i < buffers.length; i++) {
                    if (buffers[i] == null) {
                        buffers[i] = buffer;
                        buffer.refCount = 1;
                        break;
                    }
                }
            }
        }
    }

    private static class SharedBuffer {

        byte[] data;
        volatile int refCount;

        public SharedBuffer(int size) {
            data = new byte[size];
            refCount = 1;
        }

        public String toString(int bufSize) {
            StringBuilder result = new StringBuilder();
            result.append("refc=").append(refCount).append(" [");
            for (int i = 0; i<bufSize;i++) {
                if (i!=0) {
                    result.append(", ");
                }
                result.append(data[i]);
            }
            result.append("]");
            return result.toString();
        }
    }
    /** compressed input
     *
     */
    private IndexInput input;
    /** length of decompressed file
     *
     */
    private long length;
    /** current position in decompressed buffer
     *
     */
    private long bufferPos;
    /** chunk position
     * 
     */
    private int chunkPos;
    /** inflated position of the decompressed buffer
     *
     */
    private int bufferOffset;
    /** size of actual data in decompressed buffer. It can be less than
     * chunk size, because of flushes and seeks.
     */
    private int bufsize;
    /** deflated position of buffer
     *
     */
    private long bufferDeflatedPos;
    /** inflater to decompress data
     *
     */
    private ReadDataTransformer inflater;
    /** decompressed data buffer
     *
     */
    private SharedBuffer buffer;
    /** buffer to read compressed data chunks
     *
     */
    private byte[] readBuffer;
    /** chunk directory: entries of inflated position of chunks
     *
     */
    private long[] inflatedPositions;
    /** chunk directory: entries of position of compressed chunks
     *
     */
    private long[] chunkPositions;
    /** chunk directory: length if inflated data of chunks
     *
     */
    private int[] inflatedLengths;
    /** maximum size of inflated chunk, for seek optimizations
     *
     */
    private int maxInflatedLength;
    /** actual end of real data position = position of chunk directory
     *
     */
    private long endOfFilePosition;
    /** for chunk CRC calculation
     *
     */
    private CRC32 crc;
    private MemoryCache memCache;
    private String name;
    private DecompressionChunkCache cache;
    private int overwrittenChunks[];
    private int firstOverwrittenPos[];
    private final Object READ_BUFFER_LOCK = new Object();

    public TransformedIndexInput(String pName, IndexInput openInput, ReadDataTransformer inflater, DecompressionChunkCache cache) throws IOException {
        this.input = openInput;
        this.crc = new CRC32();
        bufferOffset = 0;
        bufferPos = 0;
        chunkPos = 0;
        this.name = pName;
        bufsize = 0;
        this.cache = cache;
        bufferDeflatedPos = -1;
        this.inflater = inflater;
        buffer = new SharedBuffer(512);
        readBuffer = new byte[512];
        this.memCache = new MemoryCache();

        if (input.length() >= 16) {
            length = input.readLong();
            int configLen = input.readVInt();
            byte[] config = new byte[configLen];
            input.readBytes(config, 0, configLen);
            inflater.setConfig(config);

            readChunkDirectory();
            if (chunkPositions.length > 0) {
                input.seek(chunkPositions[0]);
            }
            bufferPos = 0;
            chunkPos = 0;
            bufferOffset = 0;
            bufsize = 0;
        } else {
            throw new IOException("Invalid chunked file");
        }

        buildOverwritten();
    }

    /** check for chunks, that potentially overwrite current data and
     * merge pending write operation into buffer.
     *
     * @param currentPos
     * @throws IOException
     */
    private void checkOverwriten(long currentPos) throws IOException {
        if (inflatedPositions != null) {
            long inflatedPos = 0;
            long chunkPosition = 0;
            int obufsize = bufsize;
            for (int j = 0; j < overwrittenChunks.length; j++) {
                int i = overwrittenChunks[j];
                if (inflatedPositions[i]+inflatedLengths[i] >= bufferPos && inflatedPositions[i] < bufferPos + bufsize && currentPos < chunkPositions[i]) {
                    int tbufsize = (int) (inflatedPositions[i] - bufferPos);
                    if (tbufsize <= bufsize) {
                      //      System.out.println("Found overwrite of "+chunkPos+"("+bufferPos+":"+bufsize+") "+name+" at "+ i+"("+inflatedPositions[i]+":"+inflatedLengths[i]+")");
                        bufsize = tbufsize;
                        inflatedPos = inflatedPositions[i];
                        chunkPosition = chunkPositions[i];
                    }
                }
                // now we have to combine old data with new, that was written
                // later in the file. It can be called recursively
                if (bufsize < obufsize) {
                    // copy all data before recursive call
                    SharedBuffer original = buffer;
                    //System.out.println("orig="+original.toString(obufsize));
                    buffer = memCache.newBuffer(buffer.data.length);
                    long lpos = input.getFilePointer();
                    long obufferPos = bufferPos;
                    int overOffset = bufsize;
                    int overChunkPos = chunkPos;
                    int origBuffOffset = bufferOffset;

                    // go to chunk and read it (it can be also partiali overwritten)
                    input.seek(chunkPosition);
                    bufsize = 0;
                    chunkPos = i;
                    bufferPos = inflatedPos;
                    readDecompressImp(true);
                    //System.out.println("over="+buffer.toString(bufsize));
                    input.seek(lpos);
                    // combine results to single buffer, as it is result of readDecompressImp
                    int nbufsize = Math.max(obufsize, overOffset + bufsize);
                    //System.out.println("Merging "+bufsize+" at "+overOffset +" in "+nbufsize);
                    byte[] result = new byte[nbufsize];
                    System.arraycopy(original.data, 0, result, 0, obufsize);
                    if (overOffset<0) {
                        System.arraycopy(buffer.data, -overOffset, result, 0, bufsize+overOffset);
                    } else {
                        System.arraycopy(buffer.data, 0, result, overOffset, bufsize);
                    }
                    bufsize = nbufsize;
                    buffer.data = result;
                    //System.out.println("resu="+buffer.toString(bufsize));
                    bufferPos = obufferPos;
                    bufferOffset = origBuffOffset;
                    chunkPos = overChunkPos;
                    memCache.release(original);


                }
            }
        }
    }

    /** read chunk directory
     *
     * @throws IOException
     */
    private void readChunkDirectory() throws IOException {
        if (length < 0) {
            // if size has not been written (is -1), the directory does not exist and has
            // to be reconstructed by reading file
            scanPositions();
        } else {
            // read the directory
            // position if written at end of the file
            input.seek(input.length() - 8);
            endOfFilePosition = input.readLong();
            input.seek(endOfFilePosition);
            readDecompressImp(false);
            IndexInput in = new ByteIndexInput(buffer.data);
            // if chunk directory is large, buffers are too big, so reset them
            buffer.data = new byte[512];
            readBuffer = new byte[512];
            int entries = in.readVInt();
            inflatedPositions = new long[entries];
            chunkPositions = new long[entries];
            inflatedLengths = new int[entries];
            for (int i = 0; i < entries; i++) {
                inflatedPositions[i] = in.readVLong();
                chunkPositions[i] = in.readVLong();
                inflatedLengths[i] = in.readVInt();
            }
            in.close();
        }
        sortChunks();
//        System.out.println("Index length="+inflatedLengths.length);
    }

    /** sort chunks directory by inflated position to improve seek times
     * 
     */
    private void sortChunks() {
        Integer[] sortOrder = new Integer[inflatedPositions.length];
        for (int i = 0; i < sortOrder.length; i++) {
            sortOrder[i] = i;
        }
        Arrays.sort(sortOrder, new Comparator<Integer>() {

            public int compare(Integer o1, Integer o2) {
                long result = inflatedPositions[o1] - inflatedPositions[o2];
                if (result > 0) {
                    return 1;
                } else if (result < 0) {
                    return -1;
                } else // retain order for overwritten entries
                {
                    return o1 - o2;
                }
            }
        });
        long newInflatedPositions[] = new long[inflatedPositions.length];
        for (int i = 0; i < inflatedPositions.length; i++) {
            newInflatedPositions[i] = inflatedPositions[sortOrder[i]];
        }
        inflatedPositions = newInflatedPositions;
        long newChunkPositions[] = new long[inflatedPositions.length];
        for (int i = 0; i < inflatedPositions.length; i++) {
            newChunkPositions[i] = chunkPositions[sortOrder[i]];
        }
        chunkPositions = newChunkPositions;
        int newInflatedLengths[] = new int[inflatedPositions.length];
        for (int i = 0; i < inflatedPositions.length; i++) {
            newInflatedLengths[i] = inflatedLengths[sortOrder[i]];
            if (newInflatedLengths[i] > maxInflatedLength) {
                maxInflatedLength = newInflatedLengths[i];
            }
        }
        inflatedLengths = newInflatedLengths;
    }

    /** rebuilds directory by scanning the file. It tries to
     * recover not properly closed files without chunk directory
     * @throws IOException
     */
    private void scanPositions() throws IOException {
        long fileLen = input.length();
        List<Long> chunks = new ArrayList<Long>();
        List<Long> inflated = new ArrayList<Long>();
        List<Integer> sizes = new ArrayList<Integer>();
        length = 0;
        while (input.getFilePointer() < fileLen) {
            long chunkPos = input.getFilePointer();
            long inflatedPos = input.readVLong();
            long crc = input.readVLong();
            int chunkSize = input.readVInt();
            int inflatedSize = input.readVInt();
            chunks.add(chunkPos);
            inflated.add(inflatedPos);
            sizes.add(inflatedSize);
            length += inflatedSize;
            input.seek(input.getFilePointer() + chunkSize);
        }
        inflatedLengths = new int[sizes.size()];
        inflatedPositions = new long[sizes.size()];
        chunkPositions = new long[sizes.size()];
        for (int i = 0; i < sizes.size(); i++) {
            inflatedLengths[i] = sizes.get(i);
            inflatedPositions[i] = inflated.get(i);
            chunkPositions[i] = chunks.get(i);
        }
        sortChunks();
        endOfFilePosition = input.length();
    }

    /** find next proper chunk for sequential read. If seek operation followed by write
     * was made, next chunk is not logically next in the file. This function finds
     * next consequent chunk for bufferPos and if necessary updates offsets and
     * buffer position.
     *
     * This situation is not caught by check overwritten, since no overwriting was
     * made.
     *
     * Example chunk layout:
     *  ([pos=0,len=10], [pos = 4, len = 4], [pos=10,len=15])
     *
     * When reading chunk 0, chunk 1 is merged into 0, but chunkPos is 1. On next read
     * chunk 1 must be skipped and seek to chunk 2 must be executed.
     *
     * @return offset inside decomprsesed buffer that has current bufferPos
     * @throws IOException
     */
     private int seekToChunk() throws IOException {
        if (inflatedPositions != null) {
            if (inflatedPositions[chunkPos]==bufferPos) {
                return 0;
            }
            // for performance reason check, next chunk if it is on correct location
            if (chunkPos >= 0 && chunkPos+1 < inflatedPositions.length) {
                if (inflatedPositions[chunkPos+1] == bufferPos) {
                    return 0;
                }
            }
            final int fchunk = findFirstChunk(bufferPos);
            // check for aligned reads (tyipical situation), especially for checkOverwritten
            for (int i = fchunk; i < inflatedPositions.length && inflatedPositions[i]<=bufferPos; i++) {
                if (inflatedPositions[i] == bufferPos) {
                    if (input.getFilePointer() != chunkPositions[i]) {
                        //System.out.println("Correcting position for chunk "+i+"  loc="+inflatedPositions[i]+" oldLoc="+inflatedPositions[chunkPos]);
                        input.seek(chunkPositions[i]);
                    }
                    //System.out.println("Missed seek chunk "+chunkPos+" actual "+i+" opos="+inflatedPositions[chunkPos]+" npos="+inflatedPositions[i]);
                    chunkPos = i;
                    return 0;
                }
            }
            // in case seek write was on chunk boundary, realing the buffer and change offset
            // this is NOT generlaisation of preveus case
            // if it is called inside checkOvewritten, it is definetly inifinite loop
            // All situations like this should becaght and merged by check overwritten
            // But just for the case.
            System.out.println("Warning chunk "+chunkPos+" not cought by overwriten. Using fallback");
            for (int i = fchunk; i < inflatedPositions.length; i++) {
                if (bufferPos >= inflatedPositions[i] && bufferPos < inflatedPositions[i] + inflatedLengths[i]) {
                    int newOffset = (int) (bufferPos - inflatedPositions[i]);
                    bufferPos = inflatedPositions[i];
                    chunkPos = i;
                    if (input.getFilePointer() != chunkPositions[i]) {
                        input.seek(chunkPositions[i]);
                    }                 
                    return newOffset;
                }
            }

        }
        // seek hapened and was written beyond EOF. The hole has to be emulated
        // but might be an error
//        System.out.println("Hole at pos "+bufferPos);
//        return -1;
        throw new IOException("Chunk not found for " + name + " position " + bufferPos);
    }

    private void readDecompress() throws IOException {
        if (input.getFilePointer() >= endOfFilePosition) {
            throw new EOFException("Over EOF" + name + "  input=" + input.getFilePointer() + "  max=" + endOfFilePosition);
        }
        readDecompressImp(true);
    }

    private synchronized void readDecompressImp(boolean hasDeflatedPosition) throws IOException {
        bufferPos += bufsize;
        if (hasDeflatedPosition && bufferPos >= length) {
            throw new EOFException("Beyond eof read " + name + " " + bufferPos + ">=" + length);
        }
        int locBufferOffset = 0;
        // since next chunk could be generated by seek back and write, find proper chunk from directory
        if (hasDeflatedPosition) {
            locBufferOffset = seekToChunk();
        }
        final long currentPos = input.getFilePointer();
        byte[] cacheData = null;
        if (cache != null) {
            cache.lock(currentPos);
            cacheData = cache.getChunk(currentPos);
        }
        if (cacheData != null) {
            bufsize = cacheData.length;
            if (buffer.refCount > 1) {
                buffer.refCount--;
                buffer = memCache.newBuffer(bufsize);
            }
            if (bufsize > buffer.data.length) {
                buffer.data = new byte[bufsize];
            }
            System.arraycopy(cacheData, 0, buffer.data, 0, bufsize);
            bufferDeflatedPos = currentPos;
            if (chunkPos < chunkPositions.length - 1) {
                input.seek(chunkPositions[chunkPos + 1]);
            } else {
                input.seek(endOfFilePosition);
            }
        } else {
            if (hasDeflatedPosition) {
                final long inflatedPos = input.readVLong();
                if (bufferPos != inflatedPos) {
                    throw new IOException("Invalid compression chunk location " + bufferPos + "!=" + inflatedPos);
                }
            }
            final long chunkCRC = input.readVLong();
            final int compressed = input.readVInt();
            bufsize = input.readVInt();
            //  System.out.println("Decompressing " + input + " at " + input.getFilePointer()+" size="+bufsize);
            if (buffer.refCount > 1) {
                buffer.refCount--;
                buffer = new SharedBuffer(bufsize);
            }
            if (bufsize > buffer.data.length) {
                buffer.data = new byte[bufsize];
            }
            //System.out.println("Reading "+name+" cp="+currentPos+" dp="+bufferPos+" len="+bufsize);
            // we are at current position ie. buffer allready contains data
            if (bufferDeflatedPos == currentPos) {
                input.seek(input.getFilePointer() + compressed);
            } else {
                bufferDeflatedPos = currentPos;
                //           System.out.println("Decompress at " + currentPos + " " + cache);
                int lcnt;
                synchronized (READ_BUFFER_LOCK) {
                    if (compressed > readBuffer.length) {
                        readBuffer = new byte[compressed];
                    }
                    input.readBytes(readBuffer, 0, compressed);
                    lcnt = inflater.transform(readBuffer, 0, compressed, buffer.data, bufsize);
                }
                // did not transform
                if (lcnt < 0) {
                    lcnt = compressed;
                    System.arraycopy(readBuffer, 0, buffer.data, 0, lcnt);
                }
                if (lcnt != bufsize) {
                    throw new IOException("Incorrect buffer size " + lcnt + "!=" + bufsize);
                }
                //calculate CRC for consistency
                if (crc != null) {
                    crc.reset();
                    crc.update(buffer.data, 0, bufsize);
                    if (crc.getValue() != chunkCRC) {
                        throw new IOException("CRC mismatch");
                    }
                }
                if (firstOverwrittenPos != null && firstOverwrittenPos[chunkPos] >= 0) {
                    checkOverwriten(currentPos);
                }
                if (cache != null) {
                    cache.putChunk(currentPos, buffer.data, bufsize);
                }
            }

        }
        bufferOffset = locBufferOffset;
        chunkPos++;
    }

    @Override
    public byte readByte() throws IOException {
        if (bufferOffset >= bufsize) {
            readDecompress();
        }
        return buffer.data[bufferOffset++];
    }

    @Override
    public void readBytes(byte[] b, int boffset, int len) throws IOException {
        if (len < bufsize - bufferOffset) {
            System.arraycopy(buffer.data, bufferOffset, b, boffset, len);
            bufferOffset += len;
            return;
        }
        int llen = len;
        int loffset = boffset;
        while (llen > 0) {
            int toCopy = llen;
            if (toCopy > bufsize - bufferOffset) {
                toCopy = bufsize - bufferOffset;
            }
            System.arraycopy(buffer.data, bufferOffset, b, loffset, toCopy);

            loffset += toCopy;
            llen -= toCopy;
            bufferOffset += toCopy;

            if (bufferOffset >= bufsize && llen > 0 && input.getFilePointer() < endOfFilePosition) {
                readDecompress();
            }
        }
    }

    @Override
    public Object clone() {
        TransformedIndexInput clone = (TransformedIndexInput) super.clone();
        clone.input = (IndexInput) input.clone();
        // increase reference count to buffer, so next time someone changes data, it is duplicated
        clone.buffer.refCount++;
        // readBuffer is shared with all clones
        clone.inflater = (ReadDataTransformer) inflater.copy();
        return clone;
    }

    @Override
    public void close() throws IOException {
        input.close();
        memCache.release(buffer);
        input = null;
    }

    @Override
    public long getFilePointer() {
        return bufferPos + bufferOffset;
    }

    private int findFirstChunk(long pos) throws IOException {
        // find chunk index from indlated positions
        int i = 0;
        if (inflatedPositions.length < 100 && maxInflatedLength > 0) {
            while (i < inflatedPositions.length && !((inflatedPositions[i] <= pos) && (inflatedPositions[i] + inflatedLengths[i] > pos))) {
                i++;
            }
        } else {
            i = Arrays.binarySearch(inflatedPositions, pos - maxInflatedLength - 1) - 1;
            if (i < 0) {
                i = 0;
            }
            if (i >= inflatedLengths.length || !((inflatedPositions[i] <= pos) && (inflatedPositions[i] + inflatedLengths[i] > pos))) {
                i = 0;
            }
            while (i < inflatedPositions.length && !((inflatedPositions[i] <= pos) && (inflatedPositions[i] + inflatedLengths[i] > pos))) {
                i++;
            }
        }

        assert i >= 0 : "Invalid chunk offset table";
        // overshoot for one on purpose
        if (i >= inflatedLengths.length) {
            throw new IOException("Incorect compressed directory");
        }
        return i;
    }

    @Override
    public void seek(long pos) throws IOException {
        // check if position is in current buffer
        // System.out.println("Seek="+pos);
        if (pos >= bufferPos) {
            long ioffset = pos - bufferPos;
            if (ioffset < bufsize) {
                bufferOffset = (int) ioffset;
                return;
            }
        }
        int i = findFirstChunk(pos);
        bufferPos = inflatedPositions[i];
        chunkPos = i;
        bufsize = 0;
        input.seek(chunkPositions[i]);
        readDecompress();
        bufferOffset = (int) (pos - bufferPos);
        if (bufferOffset > bufsize) {
            throw new IOException("Incorect compressed directory");
        }
        assert bufferOffset >= 0 && bufferOffset < bufsize && bufferOffset < endOfFilePosition;
    }

    @Override
    public long length() {
        return length;
    }

    /** find chunks, that overwrite other chunks
     * 
     */
    private void buildOverwritten() {
        int tov[] = new int[inflatedPositions.length];
        int pos = 0;
        long maxPos = 0;
        long cpos = 0;
        for (int i = 0; i < inflatedPositions.length; i++) {
            cpos = inflatedPositions[i] + inflatedLengths[i];
            if (inflatedPositions[i] < maxPos) {
                tov[pos] = i;
                pos++;                
            }            
            if (cpos > maxPos) {
                maxPos = cpos;
            }
        }
        overwrittenChunks = new int[pos];
        System.arraycopy(tov, 0, overwrittenChunks, 0, pos);
        if (overwrittenChunks.length > 0) {
            firstOverwrittenPos = new int[inflatedPositions.length];
            for (int i = 0; i < inflatedPositions.length; i++) {
                firstOverwrittenPos[i] = -1;
                for (int j = 0; j < pos; j++) {
                    long bPos = inflatedPositions[overwrittenChunks[j]];
                    int bSize = inflatedLengths[overwrittenChunks[j]];
                    if (bPos >= inflatedPositions[i] && inflatedPositions[i] < bPos + bSize) {
                        firstOverwrittenPos[i] = j;
                    }
                }
            }
        }
        //System.out.println("Overwritten ="+pos);
    }
}
