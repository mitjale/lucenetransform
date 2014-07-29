/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *c
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.store.transform;

import java.io.IOException;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.transform.algorithm.StoreDataTransformer;

/** Create sequential transformed log based stream. If data is overwritten with use of seek write combination
 * original data is not changed, but additional chunk with information about new position
 * is written. It is log stuctured system, that never overwrites written data (it can
 * be used for audit data). Seek write combination introduce read overhead, since data
 * has to be merged (multiple chunks) on read access.
 *
 * @author Mitja Lenič
 */
public class TransformedIndexOutput extends AbstractTransformedIndexOutput {

    /** original data buffer
     *
     */
    private byte[] buffer;
    /** used data in buffer
     *
     */
    private int bufferOffset;

    /** position of the beginning of the buffer
     *
     */
    private long bufferPosition;

    /** max buffer offset ie. chunk size, in case of intra buffer seek
     *
     */
    private int maxBufferOffset;

    /** length of file. If position is greater then length, then actual length is position.
     * This is needed for seeks.
     */
    private long length;

    /** Create sequential log based output.
     *
     * @param name
     * @param output
     * @param pCunkSize
     * @param pLevel
     * @param deflateCount
     * @param compressedDir
     * @throws IOException
     */
    public TransformedIndexOutput(String name, IndexOutput output, int pCunkSize, StoreDataTransformer pTransformer, TransformedDirectory compressedDir) throws IOException {
        super(name, output, pTransformer, compressedDir);
        bufferOffset = 0;
        bufferPosition = 0;
        buffer = new byte[pCunkSize];
        // by writting magic number we alocate enough space for any length, since 
        // length is known and written when file is closed
        output.writeLong(MAGIC_NUMBER);
        // write configuration
        writeConfig();
    }

    @Override
    public synchronized void writeByte(byte b) throws IOException {
        if (bufferOffset >= buffer.length) {
            flushBuffer();
        }
              globalCRC.update(b);
        buffer[bufferOffset++] = b;
    }


    @Override
    public synchronized void writeBytes(byte[] b, int offset, int length) throws IOException {
          globalCRC.update(b, offset, length);
        if (length<buffer.length-bufferOffset) {
            System.arraycopy(b, offset, buffer, bufferOffset, length);
            bufferOffset+=length;
            return;
        }
        int toWrite = length;
        int woffset = offset;
        while (toWrite > 0) {
            int maxWrite = toWrite;
            if (maxWrite > buffer.length - bufferOffset) {
                maxWrite = buffer.length - bufferOffset;
            }
            if (maxWrite<0) {
                throw new IOException("Invalid flush");
            }
            System.arraycopy(b, woffset, buffer, bufferOffset, maxWrite);
            woffset += maxWrite;
            toWrite -= maxWrite;
            bufferOffset += maxWrite;
            if (bufferOffset == buffer.length) {
                flushBuffer();
            }
            if (bufferOffset>buffer.length) {
                throw new IOException("Incorrect offset "+bufferOffset+">"+buffer.length);
            }
        }
    }

    private synchronized  void flushBuffer() throws IOException {
        if (maxBufferOffset>bufferOffset) {
            bufferOffset = maxBufferOffset;
        }
        if (bufferOffset > 0) {
            writeChunk(buffer, bufferPosition,bufferOffset);
            bufferPosition +=bufferOffset;
            maxBufferOffset = bufferOffset = 0;
        }

    }

    @Override
    public synchronized  void close() throws IOException {
        // on close length information is written at the begininig of file
        flush();
        // actually close file and write chunk directory
        super.close();
    }

    @Override
    public void flush() throws IOException {
        flushBuffer();
        output.flush();
    }

    @Override
    public synchronized long getFilePointer() {
        return bufferPosition+bufferOffset;
    }

    @Override
    public long length() throws IOException {
        return getFilePointer();
    }

    @Override
    public void sync() throws IOException {
        flush();
    }

 }
