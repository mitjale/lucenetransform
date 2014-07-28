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
import java.io.OutputStream;
import org.apache.lucene.store.BufferedIndexOutput;

/** Writes index output to stream. Used in creating chunk directory.
 *
 * @author Mitja Lenič
 */
public class StreamIndexOutput extends BufferedIndexOutput {
    private OutputStream stream;
    private long length;

    public StreamIndexOutput(OutputStream stream) {
        this.stream = stream;
        length = 0;
    }



    @Override
    protected void flushBuffer(byte[] b, int offset, int len) throws IOException {
        stream.write(b,offset,len);
        length +=len;
    }

    @Override
    public long length() throws IOException {
        return length;
    }

    @Override
    public void close() throws IOException {
        super.close();
        stream.close();

    }



}
