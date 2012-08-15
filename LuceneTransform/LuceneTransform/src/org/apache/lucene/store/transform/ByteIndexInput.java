
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
import org.apache.lucene.store.BufferedIndexInput;

/** Create IndexInput from byte array. This class is used to read
 * directory info, which is compressed in single chunk.
 *
 * @author Mitja Lenič
 */
public class ByteIndexInput extends BufferedIndexInput {
    private byte[] data;
    private int pos;

    public ByteIndexInput(String name, byte[] data) {
        super(name);
        this.data = data;
        pos = 0;
    }


    @Override
    protected void readInternal(byte[] b, int offset, int length) throws IOException {
        System.arraycopy(data, pos, b, offset, length);
        pos+=length;
    }

    @Override
    protected void seekInternal(long pos) throws IOException {
        this.pos = (int)pos;
    }

    @Override
    public void close() throws IOException {        
    }

    @Override
    public long length() {
        return data.length;
    }

}
