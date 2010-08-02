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

package org.apache.lucene.store.transform.algorithm;

import java.io.IOException;

/** Enables combination of two data transformations, eg. compression and encryption.
 * The order is important. First use compression then encryption.
 *
 * @author Mitja Lenič
 */
public abstract class AbstractPipedTransformer implements DataTransformer {
    protected DataTransformer first;
    protected DataTransformer second;
    private byte[] data;

    protected AbstractPipedTransformer(DataTransformer first, DataTransformer second) {
        this.first = first;
        this.second = second;
        data = new byte[512];
    }

    public int transform(byte[] inBytes, int inOffset, int inLength, byte[] outBytes, int maxOutLength) throws IOException {
        if (data.length<maxOutLength) {
            data = new byte[maxOutLength];
        }
      int cnt = first.transform(inBytes, inOffset, inLength, data, maxOutLength);
      if (cnt<0) {
          cnt = inLength;
          System.arraycopy(inBytes, inOffset, data, 0, cnt);
      }
      int cnt2 = second.transform(data, 0, cnt, outBytes, maxOutLength);
      if (cnt>=0 && cnt2<0) {
          System.arraycopy(data, 0, outBytes, 0, cnt);
          return cnt;
      }
      return cnt2;
    }

}
