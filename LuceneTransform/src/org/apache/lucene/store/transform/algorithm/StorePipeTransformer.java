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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.transform.StreamIndexOutput;

/** Combines configuration of two piped transformation.
 *
 * @author Mitja Lenič
 */
public class StorePipeTransformer extends AbstractPipedTransformer implements StoreDataTransformer {

    public StorePipeTransformer(StoreDataTransformer first, StoreDataTransformer second) {
        super(first, second);
    }

    public DataTransformer copy() {
        return new StorePipeTransformer((StoreDataTransformer)first.copy(), (StoreDataTransformer)second.copy());
    }

    public byte[] getConfig() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IndexOutput out = new StreamIndexOutput(bos);
        byte[] configFirst = ((StoreDataTransformer)first).getConfig();
        byte[] configSecond = ((StoreDataTransformer)second).getConfig();
        out.writeVInt(configFirst.length);
        out.writeBytes(configFirst,configFirst.length);
        out.writeVInt(configSecond.length);
        out.writeBytes(configSecond,configSecond.length);
        out.close();
        return bos.toByteArray();

    }
}
