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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.transform.algorithm.StoreDataTransformer;

/** Abstract base class for writting compressed chunk file.
 *
 * @author Mitja Lenič
 */
public abstract class AbstractTransformedIndexOutput extends IndexOutput {



    /** used to store compressed chunk directory information. This information
     * is used to find (and merge) information about possible chunk overwrites,
     * that are result of seek and write operation.
     *
     */
    private static class DirectoryEntry implements Comparable<DirectoryEntry> {

        /** original inflated position
         *
         */
        private Long inflatedPos;
        /** chunk position in file
         *
         */
        private Long deflatedPos;
        /** length of inflated chunk (original data size).
         *
         */
        private Integer length;

        public DirectoryEntry(Long inflatedPos, Long deflatedPos, Integer length) {
            this.inflatedPos = inflatedPos;
            this.deflatedPos = deflatedPos;
            this.length = length;
        }

        public Long getDeflatedPos() {
            return deflatedPos;
        }

        public Long getInflatedPos() {
            return inflatedPos;
        }

        public Integer getLength() {
            return length;
        }


        /** compera based on original position. Can be used to improve
         * performance on reads
         *
         * @param t
         * @return
         */
        public int compareTo(DirectoryEntry t) {
            if (inflatedPos < t.inflatedPos) {
                return -1;
            } else if (inflatedPos > t.inflatedPos) {
                return 1;
            } else {
                return 0;
            }
        }
    }
    /** original output file
     *
     */
    protected IndexOutput output;
    /** deflater used for deflation
     *
     */
    private StoreDataTransformer deflater;
    /** buffer for defated data
     *
     */
    private byte[] deflatedBuffer;
    /** reference to compressed directory to inform it about close
     *
     */
    private TransformedDirectory compressedDir;
    private CRC32 crc;
    /** directory chunk entries
     *
     */
    private List<DirectoryEntry> chunkDirectory;
    /** name of the file
     *
     */
    protected String name;

    public AbstractTransformedIndexOutput(String name, IndexOutput output, StoreDataTransformer deflater, TransformedDirectory compressedDir) {
        this.name = name;
        this.output = output;
        this.deflater = deflater;
        this.compressedDir = compressedDir;
        this.deflatedBuffer = new byte[4096];
        this.chunkDirectory = new ArrayList<DirectoryEntry>();
        this.crc = new CRC32();

    }

    protected void writeConfig() throws IOException {
        byte[] config = deflater.getConfig();
        if (config != null) {
            output.writeVInt(config.length);
            output.writeBytes(config, config.length);
        } else {
            output.writeVInt(0);
        }
    }

    protected synchronized void writeChunk(byte[] pData, long pPosition, int pSize) throws IOException {
        // add original position in stream   and  compressed chunk position

        addDirectoryEntry(pPosition, output.getFilePointer(), pSize);


        // write inflated position for directory reconstruction
        // in case of interruption
        output.writeVLong(pPosition);

        //write chunk
        writeChunkImp(pData, pSize);
    }

    private synchronized void writeChunkImp(byte[] pData, int pSize) throws IOException {

        // deflate original data
        if (deflatedBuffer.length < pData.length * 2) {
            deflatedBuffer = new byte[pData.length * 2];
        }
        int count = deflater.transform(pData, 0, pSize, deflatedBuffer, deflatedBuffer.length);

        //calculate CRC for consistency
        crc.reset();
        crc.update(pData, 0, pSize);
        output.writeVLong(crc.getValue());

        // if no trasnformation is implemented, write original data
        if (count < 0) {
            // write deflated chunk size
            output.writeVInt(pSize);

            // write original size
            output.writeVInt(pSize);
            // write original data
            output.writeBytes(pData, pSize);
        } else {
            // write deflated chunk size
            output.writeVInt(count);

            // write original size
            output.writeVInt(pSize);

            // write deflated data
            output.writeBytes(deflatedBuffer, count);
        }

    }

    private void addDirectoryEntry(Long pInflatedPos, Long pDeflatedPos, Integer pLength) {
        chunkDirectory.add(new DirectoryEntry(pInflatedPos, pDeflatedPos, pLength));      
    }

    public synchronized void close() throws IOException {

       
        long directoryPos = length();
        // create chunk directory in separate single compressed chunk
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IndexOutput boutput = new StreamIndexOutput(bos);

        // write number of chunk entries
        boutput.writeVInt(chunkDirectory.size());
        for (int i = 0; i < chunkDirectory.size(); i++) {
            boutput.writeVLong(chunkDirectory.get(i).getInflatedPos());
            boutput.writeVLong(chunkDirectory.get(i).getDeflatedPos());
            boutput.writeVInt(chunkDirectory.get(i).getLength());
        }
        boutput.flush();
        boutput.close();
        byte dir[] = bos.toByteArray();

        // mark position of compressed chunk of chunk directory
        long entryPos = output.getFilePointer();

        // compress chunk directory into single chunk
        writeChunkImp(dir, dir.length);

        // write chunk directory postion at end of the file
        output.writeLong(entryPos);
        output.flush();
        updateFileLength(directoryPos);
        output.close();
        compressedDir.release(name);


    }

    /** sync (flush) uncompressed data, to ensure everything is written.
     * Used to implement directory sinc {@link Directory.sync}
     * @throws IOException
     */
    public abstract void sync() throws IOException;


    /** write length of file on the begining of file to indicate successfull close and that directory has been
     * successfully written
     * @param pLength actual length of file
     */
    protected abstract void updateFileLength(long pLength) throws IOException;
}
