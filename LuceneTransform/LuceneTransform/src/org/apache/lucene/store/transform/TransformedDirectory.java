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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.transform.algorithm.ReadDataTransformer;
import org.apache.lucene.store.transform.algorithm.StoreDataTransformer;

/** <p>Provides transparent compression for underlying directory.</p>
 * 
 * <p> It uses java Deflater  {@link Deflater} to compress data into chunks. Chunks are independently
 * compressed to enable faster seeking in data. Chunks can be compressed multiple times,
 * to improve compression ratio. Data storage is delegated to nested directory</p>
 * 
 * <p>Chunks should not be to small in size, nor to large. 
 * Small chunk size introduces additional data overhead and reduces the chances of good
 * compression. Larger chunks can result in better compression but increases the 
 * overhead in reading (searching), since larger chunk has to be decompressed to obtain part of data. 
 * Chunk size is only important when writing to directory. Read chunk size is determined automatically. </p>
 * 
 * <p> Seeks in the file writes represent a problem, since already compressed data is changed on specific location.
 * In Lucene seeks are used to update index data (like offsets). There are two ways to tackle with seek (writes) in compressed directory:
 *  
 * 
 * <ul>
 * 		<li>temporary files; uses temporary directory to create file and compress it on close operation. This
 * has advantage to create clean compressed chunked file, without additional seeks, should have better read performance,
 * but requires more space when creating index. If you have fast disks and enough space (uncompressed&compressed index) for uncompressed index during indexing, that is proper choice.</li>
 * 		<li>direct compression; directly write compressed stream where seek operation do not actually overwrite (or recompress) existing data, but additional chunk is appended 
 *          with information of its location. This requires more time when reading the file, since all pending modification have to be merged during the read.
 *          In current implementation of Lucene, there are not so many seek and write operations, therefore performance hit is bearable. You should use this approach, also if 
 *          you don't have enough space to store uncompressed index during indexing.</li>
 * </ul>
 *
 * @author Mitja Lenič
 */
public class TransformedDirectory extends Directory {

    /** Nested directory that actually stores data
     *
     */
    private final Directory nested;

    /** Temporary directory to store uncompressed files in case of temporary files
     *
     */
    private final Directory tempDirectory;

    /** Write chunk size
     * 
     */
    private final int chunkSize;

    /** track of open outputs to properly close them (which is very important) on directory close
     * 
     */
    private final Map<String, AbstractTransformedIndexOutput> openOutputs;
    /** if we use direct compression approach
     * 
     */
    private final boolean directStore;

    /** sequential number for temporary name suffix, in case temporary directory is the same
     * as nested (storage) directory.
     */
    private int seqNum;

    /** chunk transformation for writing
     *
     */
    private StoreDataTransformer storeTransformer;

    /** chunk transformation for reading. Must be inverse transformation of writing.
     *
     */
    private ReadDataTransformer readTransformer;

    /** default chunk cache size
     *
     */
    private int cacheSize = 100;

    private static SharedBufferCache memCache = new SharedBufferCache();

    /** Create compressed directory, that utilizes direct compression with default parameters.
     *
     * @param nested directory to store compressed data.
     * @param storeTransformer transformation for writing chunks
     * @param readTransformer  transformation for reading chunks (inverse of storeTransformer)
     */
    public TransformedDirectory(final Directory nested,StoreDataTransformer storeTransformer, ReadDataTransformer readTransformer) {
        this(nested, storeTransformer, readTransformer, true);
    }

    /** Create compressed directory with default parameters
     *
     * @param nested directory to store compressed data.
     * @param storeTransformer transformation for writing chunks
     * @param readTransformer  transformation for reading chunks (inverse of storeTransformer)
     * @param directStore if true, direct compression is be used, otherwise temporary files in directory are used. The same directory is used to store final and temporary files
     */
    public TransformedDirectory(final Directory nested, StoreDataTransformer storeTransformer, ReadDataTransformer readTransformer, boolean directStore) {
        this(nested, 128 * 1024, storeTransformer, readTransformer, directStore);
    }

    /** Create compressed directory.
     *
     *
     * @param nested directory to store compressed data.
     * @param chunkSize size of the chunk. should be minimum 4K, default is 128K
     * @param storeTransformer transformation for writing chunks
     * @param readTransformer  transformation for reading chunks (inverse of storeTransformer)
     * @param directStore if true, direct compression is be used, otherwise temporary files in directory are used. The same directory is used to store final and temporary files.
     */
    public TransformedDirectory(final Directory nested, final int chunkSize, StoreDataTransformer storeTransformer, ReadDataTransformer readTransformer, boolean directStore) {
        this(nested, nested, chunkSize, storeTransformer, readTransformer, directStore);
    }

    /** Create compressed directory.
     *
     * @param nested directory to store compressed data.
     * @param tempDir directory to store temporary data.
     * @param chunkSize size of the chunk. should be minimum 4K, default is 128K
     * @param storeTransformer transformation used to store data (ex. Deflater)
     * @param readTransformer  transformation used to read data (ex. Inflater)
     * @param directStore if true, direct compression is be used, otherwise temorarry files in directory are used.
     */
    public TransformedDirectory(final Directory nested, final Directory tempDir, final int chunkSize, StoreDataTransformer storeTransformer, ReadDataTransformer readTransformer,boolean directStore) {
        this.seqNum = 0;
        this.nested = nested;
        this.chunkSize = chunkSize;
        this.openOutputs = new HashMap<String, AbstractTransformedIndexOutput>();
        this.tempDirectory = tempDir;
        this.directStore = directStore;
        this.storeTransformer = storeTransformer;
        this.readTransformer = readTransformer;
    }

    @Override
    public String[] listAll() throws IOException {
        return nested.listAll();
    }

    @Override
    public boolean fileExists(String name) throws IOException {
        return nested.fileExists(name);
    }



    @Override
    public void deleteFile(String name) throws IOException {
        nested.deleteFile(name);
    }

    @Override
    public long fileLength(String name) throws IOException {
        IndexInput input = nested.openInput(name, null);
        long length = input.readLong();
        input.close();
        // information is not correct
        // open and recalculate size
        if (length==-1) {
            IndexInput in = openInput(name,null);
            length = in.length();
            in.close();
        }
        return length;
    }

    @Override
    public IndexOutput createOutput(String name,IOContext ioc) throws IOException {
        IndexOutput out = nested.createOutput(name,ioc);
        AbstractTransformedIndexOutput output;
        if (directStore) {
            output = new SequentialTransformedIndexOutput(name, out, chunkSize, (StoreDataTransformer)storeTransformer.copy(), this);
        } else {
            output = new TransformedIndexOutput(this, tempDirectory, out, name, chunkSize, (StoreDataTransformer)storeTransformer.copy(),ioc);
        }
        synchronized (openOutputs) {
            openOutputs.put(name, output);
        }
        return output;
    }

    @Override
    public IndexInput openInput(String name,IOContext ioc) throws IOException {
        DecompressionChunkCache cache=null;
        if (cacheSize>0) {
            cache = new LRUChunkCache(cacheSize);
        }
        return new TransformedIndexInput(name,nested.openInput(name,ioc), (ReadDataTransformer) readTransformer.copy(),cache,memCache);
    }

    @Override
    public void close() throws IOException {
        synchronized (openOutputs) {
            Collection<AbstractTransformedIndexOutput> copy = new ArrayList<AbstractTransformedIndexOutput>(openOutputs.values());
            for (AbstractTransformedIndexOutput out : copy )
                out.close();
        }
        nested.close();
    }

    @Override
    public void clearLock(String name) throws IOException {
        nested.clearLock(name);
    }

    @Override
    public LockFactory getLockFactory() {
        return nested.getLockFactory();
    }

    @Override
    public String getLockID() {
        return nested.getLockID();
    }

    @Override
    public Lock makeLock(String name) {
        return nested.makeLock(name);
    }

    @Override
    public void setLockFactory(LockFactory lockFactory) throws IOException {
        nested.setLockFactory(lockFactory);
    }

    @Override
    public void sync(Collection<String>  names) throws IOException {
        /* sync all open outputs. It can result in smaller chunk sizes
         *
         */
        AbstractTransformedIndexOutput output;
        synchronized (openOutputs) {
            for (String name:names) {
              output = openOutputs.get(name);
                if (output != null) {
                output.sync();
                }
            }
        }
        nested.sync(names);
    }

    /** obtain next temporal postfix sequence
     *
     * @return
     */
    synchronized int nextSequence() {
        return ++seqNum;
    }

    /** called when writer is closed
     *
     * @param pName
     */
    void release(String pName) {
        synchronized (openOutputs) {
            openOutputs.remove(pName);
        }
    }

    public int getCachedPageCount() {
        return cacheSize;
    }

    public void setCachePageCount(int pCached) {
        cacheSize = pCached;
    }

}
