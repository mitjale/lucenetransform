package org.apache.lucene.store.transform;
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

import java.io.IOException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.transform.algorithm.StoreDataTransformer;

/** Creates compressed output by using temporary files. Transformation is implemented on close.
 * This is the safest way to perform transformation and yields in better read performance.
 *
 * @author Mitja Lenič
 */
public class TransformedIndexOutput extends AbstractTransformedIndexOutput {

    /** directory to store temporary files
     *
     */
    private Directory tempDirectory;
    /** name of temporary file
     *
     */
    private String tmpName;
    /** temporary output, to wich all calls are routed until close
     *
     */
    private IndexOutput tempOut;
    /** compression chunk size
     *
     */
    private int chunkSize;

    /** length on close, to preserve information after deletion
     * 
     */
    private long closeLength=-1;


    /** creates compressed output.
     *
     * @param pCompressed compressed directory to inform when file is closed
     * @param pTempDir temporary directory that can be the asame as compressed one
     * @param pOut actual IndexOutput from compresed directory (nested storage)
     * @param pName name of output
     * @param pChunkSize  chunk size
     * @param pDeflateMethod deflate  method
     * @param deflateCount number of times to deflate data
     * @throws IOException
     */
    public TransformedIndexOutput(TransformedDirectory pCompressed, Directory pTempDir, IndexOutput pOut, String pName, int pChunkSize, StoreDataTransformer pTransformer) throws IOException {
        super(pName,pOut, pTransformer, pCompressed);
        this.tempDirectory = pTempDir;
        this.chunkSize = pChunkSize;
        tmpName = pName + ".plain." + pCompressed.nextSequence()+".tmp";
        tempOut = pTempDir.createOutput(tmpName);
    }

    @Override
    public void writeByte(byte b) throws IOException {
        tempOut.writeByte(b);
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
        tempOut.writeBytes(b, offset, length);
    }

    @Override
    public void flush() throws IOException {
        tempOut.flush();
    }

    /** closes temporary file, compresses data and removes temporary file.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        byte[] buffer = new byte[chunkSize];
        tempOut.close();
        // directory with offsets offsets of compressed chunks with
        // real position in decompressed stream
        IndexInput in = tempDirectory.openInput(tmpName);
        long len = closeLength = in.length();
        // write length of the file at the begining for easier retreval 
        output.writeLong(-1);
        
        // write configuration
        writeConfig();
        int toRead;
        // read all data and compresse it in variable block chunks
        while (len > 0) {
            if (len > buffer.length) {
                toRead = buffer.length;
            } else {
                toRead = (int) len;
            }


            // just for safety --- can be improoved 
            long bufferPos = in.getFilePointer();
            // read original data
            in.readBytes(buffer, 0, toRead);

            writeChunk(buffer, bufferPos, toRead);

            len -= toRead;
        }
        // now let's crate directory entry of all chunks and their's original
        // position in inflated stream

        in.close();
        if (tempDirectory.fileExists(tmpName)) {
            tempDirectory.deleteFile(tmpName);
        }  
        super.close();

    }

    @Override
    public long getFilePointer() {
        return tempOut.getFilePointer();
    }

    @Override
    public void seek(long l) throws IOException {
        tempOut.seek(l);
    }

    @Override
    public long length() throws IOException {
        if (closeLength>=0) {
            return closeLength;
        }
        return tempOut.length();
    }

    public void sync() throws IOException {
        flush();
    }

    @Override
    protected void updateFileLength(long pLength) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
