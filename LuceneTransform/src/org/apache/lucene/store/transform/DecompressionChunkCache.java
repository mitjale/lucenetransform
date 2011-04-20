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
 * @author Mitja Lenič
 */
public interface DecompressionChunkCache {
    /** return plain chunk at file position
     *
     * @param pos position in plain file
     * @return chunk data, with exact array length or null if chunk is not in the cache
     */
    byte[] getChunk(long pos);
    /** lock chunk position for modification. To prevent multiple transformations, position
     * must be locked when performing transformation.
     *
     * @param pos position in plain file
     */
    void lock(long pos);

    /** unlocks position for reading
     *
     * @param pos in plain file
     */
    void unlock(long pos);
    /** store chunk in the cache and release obtained lock. Must copy data into
     * internal buffer.
     *
     * @param pos plain chunk position
     * @param data data buffer, can be larger than actual chunk size
     * @param pSize actual chunk size.
     */
    void putChunk(long pos, byte[] data, int pSize);

    /** clear chunk cache
     * 
     */
    void clear();
}
