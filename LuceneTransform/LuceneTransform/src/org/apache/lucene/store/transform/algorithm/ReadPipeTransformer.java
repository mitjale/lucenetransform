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
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.transform.ByteIndexInput;

/** Reads combination of two configurations of piped transformation.
 *
 * @author Mitja Lenič
 */
public class ReadPipeTransformer extends AbstractPipedTransformer implements ReadDataTransformer {

    public ReadPipeTransformer(ReadDataTransformer first, ReadDataTransformer second) {
        super(first,second);
    }

    public DataTransformer copy() {
        return new ReadPipeTransformer((ReadDataTransformer)first.copy(), (ReadDataTransformer)second.copy());
    }

    public void setConfig(byte[] pData) throws IOException {
          IndexInput input = new ByteIndexInput(pData);
          // in different order of course 
          int secondLen = input.readVInt();
          byte[] secondConfig = new byte[secondLen];
          input.readBytes(secondConfig, 0, secondLen);

          int firstLen = input.readVInt();
          byte[] firstConfig = new byte[firstLen];
          input.readBytes(firstConfig, 0, firstLen);
          input.close();
          ((ReadDataTransformer)first).setConfig(firstConfig);
          ((ReadDataTransformer)second).setConfig(secondConfig);


    }

}
