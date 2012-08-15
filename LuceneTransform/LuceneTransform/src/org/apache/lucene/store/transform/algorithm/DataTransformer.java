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

/** Performs transformation of data
 *
 * @author Mitja Lenič
 */
public interface DataTransformer extends Cloneable {

    /** create deep copy, for single file transformation for use in multiple threads.
     * If transformer has no state and is thread safe, same instance can be returned.
     * 
     * @return
     */
    DataTransformer copy();
    /** transforms input data, using target algorithm to output data
     *
     * @param inBytes input data buffer
     * @param inOffset offset of data in input buffer
     * @param inLength length of input data
     * @param outBytes output buffer (if filled starting at offset 0)
     * @param maxOutLength maximum size
     * @return -1 if no data transformation is necessary, or size of transformed data stored in outBytes, started at index 0
     * @exception if transformation could not be performed, due to IO error or insufficient memory.
     */
    int transform(byte[] inBytes, int inOffset, int inLength, byte[] outBytes, int maxOutLength) throws IOException;

}
