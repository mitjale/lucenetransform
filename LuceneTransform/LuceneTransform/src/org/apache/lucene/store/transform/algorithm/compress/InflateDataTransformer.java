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
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.apache.lucene.store.transform.algorithm.ReadDataTransformer;

/** Inflates (decompreses) chunk. If chunk size equals maximum size, original is returned.
 *
 * @author Mitja Lenič
 */
public class InflateDataTransformer implements ReadDataTransformer {
    private Inflater inflater;
    private int inflateCount;

    private InflateDataTransformer(int inflateCount) {
        this.inflateCount = inflateCount;
        this.inflater = new Inflater();
    }

    public InflateDataTransformer() {
        this(2);
    }



    public synchronized int transform(byte[] inBytes, int inOffset, int inLength, byte[] outBytes, int maxOutLength) throws IOException {
        // just for sematically corect
        // and if deflated and inflated size are equal (there was no trasform)
        if (inflateCount<=0 || inLength == maxOutLength) {
            return -1;
        }
        byte[] buffer = inBytes;
        int offset = inOffset;
        int length = inLength;
        for (int i = 0; i<inflateCount;i++) {
            inflater.reset();
            inflater.setInput(buffer, offset, length);
            try {
                length = inflater.inflate(outBytes, 0, maxOutLength);
            } catch (DataFormatException ex) {
                throw new IOException(ex);
            }
            buffer = outBytes;
            offset = 0;
        }
        return length;
    }

    public DataTransformer copy() {
        //return new InflateDataTransformer(inflateCount);
        return this;
    }

    public void setConfig(byte[] pData) {
        inflateCount = pData[0];
    }


}
