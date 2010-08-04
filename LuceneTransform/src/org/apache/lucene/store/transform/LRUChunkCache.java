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

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Simple LRU implementation of chunk cache.
 *
 * @author Mitja Lenič
 */
public class LRUChunkCache implements DecompressionChunkCache {

    private Map<Long, SoftReference<byte[]>> cache;
    private final Map<Long, Object> locks;
    private int cacheSize;
    private long hit;
    private long miss;

    public LRUChunkCache(int pCacheSize) {
        this.cacheSize = pCacheSize;
        this.locks = new HashMap<Long, Object>();
        cache = new LinkedHashMap<Long, SoftReference<byte[]>>(this.cacheSize, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, SoftReference<byte[]>> eldest) {
                return size() >= LRUChunkCache.this.cacheSize;
            }
        };

    }

    public synchronized byte[] getChunk(final long pos) {
        final SoftReference<byte[]> w = cache.get(pos);
        if (w != null) {
            final byte[] result = w.get();
            if (result != null) {
                hit++;
                freeLock(pos);
            } else {
                cache.remove(pos);
                miss++;
            }
            return result;
        }
        miss++;
        return null;
    }

    public synchronized void putChunk(final long pos, final byte[] data, final int pSize) {
        try {
            if (cache.size() > cacheSize) {
                //OPS how is this possible                
                cache.clear();
            }
            final byte copy[] = new byte[pSize];
            System.arraycopy(data, 0, copy, 0, pSize);
            cache.remove(pos);
            cache.put(pos, new SoftReference<byte[]>(copy));
            freeLock(pos);

        } catch (OutOfMemoryError ex) {
            cache.clear();          
        }
    }

    private void freeLock(long pos) {
        Object lock;
        synchronized (locks) {
            lock = locks.get(pos);
            if (lock != null) {
                synchronized (lock) {
                    lock.notifyAll();
                }
                locks.remove(pos);
            }
        }
    }

    public void clear() {
        cache.clear();
    }

    @Override
    protected void finalize() throws Throwable {
        //System.out.println("Hit:" + hit + "/" + miss + " " + (hit * 100 / (hit + miss)) + "%");
        super.finalize();
    }

    public void lock(long pos) {
        Object lock = null;
        synchronized (locks) {
            lock = locks.get(pos);
            if (lock == null) {
                lock = new Object();
                locks.put(pos, lock);
                return;
            }
        }
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ex) {
                Logger.getLogger(LRUChunkCache.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public String toString() {
        return "LRUCache pages="+cache.size()+" locks="+locks.size();
    }
}
