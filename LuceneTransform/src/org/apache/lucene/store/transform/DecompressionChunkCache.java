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

/** Interface for chunk cache. Since transformation/decompression on the chunk can be
 * expensive, especially when accessing index directory, this interface provides access to
 * previously read chunks. Each directory file can have its own chunk cache.
 *
 * @author Mitja LeniÄ�
 */
public interface DecompressionChunkCache {
    /** Returns the chunk for this position, or locks this position
     * (so other threads making the same request will wait) and returns null.
     * If this function returns null, you *must* call putChunkAndUnlock().
     * 
     * @param pos position in plain file
     * @return chunk data, with exact array length or null if chunk is not in the cache
     */
    byte[] getChunkOrLock(long pos);

    /** Stores a chunk (if provided) and unlocks this position.  This position must be locked!
     * 
     * @param pos plain chunk position
     * @param data data buffer, can be larger than actual chunk size
     * @param pSize actual chunk size.
     */
    void putChunkAndUnlock(long pos, byte[] data, final int pSize);

    /** clear chunk cache
     * 
     */
    void clear();
}
