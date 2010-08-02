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

package org.apache.lucene.store.transform.algorithm.compress;

import org.apache.lucene.store.transform.algorithm.DataTransformer;
import java.util.zip.Deflater;
import org.apache.lucene.store.transform.algorithm.StoreDataTransformer;

/** Compresses (deflates) the chunk. If compressed chunk is bigger or equal in
 * size as original, original chunk is used.
 *
 * @author Mitja Lenič
 */
public class DeflateDataTransformer implements StoreDataTransformer {

    private Deflater deflater;
    private int deflateCount;
    private int deflateMethod;

    public DeflateDataTransformer(int deflateMethod, int deflateCount) {
        this.deflateCount = deflateCount;
        this.deflater = new Deflater(deflateMethod);
        this.deflateMethod = deflateMethod;
    }

    public DeflateDataTransformer() {
        this (Deflater.DEFAULT_COMPRESSION,1);
    }



    public int transform(byte[] inBytes, int inOffset, int inLength, byte[] outBytes, int maxOutLength) {
        // just for sematically corect interpretation
        if (deflateCount<=0) {
            return -1;
        }
        byte[] buffer = inBytes;
        int offset = inOffset;
        int length = inLength;
        for (int i = 0; i<deflateCount;i++) {
            deflater.reset();
            deflater.setInput(buffer, offset, length);
            deflater.finish();
            length = deflater.deflate(outBytes,0,maxOutLength);
            buffer = outBytes;
            offset = 0;
        }
        if (length>=inLength) {
            return -1;
        }
        return length;
    }

    public DataTransformer copy() {
        return new DeflateDataTransformer(deflateMethod, deflateCount);
    }

    public byte[] getConfig() {
        return new byte[] {(byte) deflateCount};
    }

}
