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

/** Simple LRU implementation of chunk cache, shared among a set of TransformedIndexInput clones.
 *
 * @author Mitja LeniÄ�
 */
public class LRUChunkCache implements DecompressionChunkCache {

	/** We implement locks with this object.  First, we lock the cache object whenever we use it.
	  * Second, we want locks to enforce that, if several callers want data at the same position,
	  * one should get the data and the rest should wait on that one.
	  * To enforce this, we lock the key corresponding to the position while retrieving that data.
	  * This is not a great solution.  There are a number of cases where we can retrieve data we
	  * already have.  But it usually works and it won't deadlock.
	  */
    private final Map<Long, SoftReference<byte[]>> cache;
    private final Map<Long, Object> locks;
    private final int cacheSize;
    private long hit; // not threadsafe
    private long miss; // not threadsafe

    static private int numCaches = 0;
    private final int cacheIndex;
    private int numLocks;
    
    public LRUChunkCache(int pCacheSize) {
        this.cacheSize = pCacheSize;
        this.locks = new HashMap<Long, Object>();
        cache = new LinkedHashMap<Long, SoftReference<byte[]>>(this.cacheSize, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, SoftReference<byte[]>> eldest) {
                return size() >= LRUChunkCache.this.cacheSize;
            }
        };

        cacheIndex = numCaches++; // This is not threadsafe, but it's only for debugging purposes.
        numLocks = 0;
    }

    public synchronized byte[] getChunk(final long pos) {
        final SoftReference<byte[]> w = cache.get(pos);
        if (w != null) {
            final byte[] result = w.get();
            if (result != null) {
                hit++;
            } else {
                cache.remove(pos);
                miss++;
            }
            return result;
        }
        miss++;
        return null;
    }

    /** Returns the chunk for this position or locks this position
     * (so other threads making the same request will wait) and returns null.
     */
    public byte[] getChunkOrLock(final long pos) {
		Object lock = new String("dummy"); // Create a dummy object to lock in the first pass.
    	boolean trackResult = true;
    	int iterNum = 0;

    	while (true) { // wait() should always be called in a loop.
	    	synchronized (lock) {
	    		synchronized (this) {
	    			// See if we have the chunk cached.
	    			final byte[] result = getChunkImp(pos, trackResult);

	    			trackResult = false;
		    		if (result != null) {
		    			return result;
		    		}
		    		// See if the lock we have is the current one.
	    			Object currentLock = locks.get(pos);

	    			if (currentLock == null) {
	    				// There is no current lock, so set one and return null.
	    				// That tells the caller to retrieve the chunk.
	    				Object newLock = new Integer(numLocks++);

	    				locks.put(pos, newLock);
	    				return null;
	    			}
	    			if (lock != currentLock) {
	    				// We want to wait on currentLock, but we must synchronize on it first.
	    				lock = currentLock;
	    				continue;
	    			}
	    		}
    			// Our lock is up to date!  Wait on it (and while not synchronizing on this object).
    			try {
    				lock.wait();
    			}
    			catch (InterruptedException ex) {
    				// wait() says this can happen.
    			}
	    	}
    	}
    }

    /** Wraps pulling a chunk out of cache. */
    private synchronized byte[] getChunkImp(final long pos, boolean trackResult) {
        final SoftReference<byte[]> w = cache.get(pos);

        if (w != null) {
            final byte[] result = w.get();
            if (result != null) {
            	if (trackResult) {
            		hit++;
            	}
	            return result;
            } else {
                cache.remove(pos);
            }
        }
    	if (trackResult) {
    		miss++;
    	}

        return null;
    }

    public void putChunkAndUnlock(final long pos, final byte[] data, final int pSize) {
    	Object lock;

    	synchronized (this) {
        	// Store the chunk.
    		if (data != null) {
	            try {
		            if (cache.size() > cacheSize) {
		                //OPS how is this possible                
		            	assert(false);
		                cache.clear();
		            }
		            assert(pSize > 0);
		            final byte copy[] = new byte[pSize];
		            System.arraycopy(data, 0, copy, 0, pSize);
		            cache.remove(pos); // so that this entry winds up being the LRU item
		            cache.put(pos, new SoftReference<byte[]>(copy));
		            	// Note: This may bump another entry out of cache.
	            } catch (OutOfMemoryError ex) {
	                cache.clear();
	            }
    		}
        	// Get the lock.
            lock = locks.get(pos);
            if (lock == null) {
            	throw new IllegalStateException("Unlock only when this position is locked!");
            }
    	}
    	synchronized (lock) {
    		// Remove the lock.
    		Object currentLock;

    		synchronized (this) {
    			currentLock = locks.remove(pos);
    			assert(lock == currentLock);
    		}
        	// Finally, notify on the lock (to unfreeze threads waiting in getChunkOrLock()).
    		lock.notifyAll();
    		if (currentLock != lock) { // just in case
    			currentLock.notifyAll();
    		}
    	}
    }
    
    public synchronized void clear() {
        cache.clear();
    }

    @Override
    protected void finalize() throws Throwable {
        //System.out.println("Hit:" + hit + "/" + miss + " " + (hit * 100 / (hit + miss)) + "%");
        super.finalize();
    }

    @Override
    public String toString() {
        return "LRUCache pages=" + cache.size() + " locks=" + locks.size();
    }
}
