package org.apache.lucene.store.transform;

/**
 * allocation is key performance problem, therefore every malloc we can
 * avoid is ok. MemoryCache is shared instance between original and all clones.
 */
public class SharedBufferCache {

    public static class SharedBuffer {

        byte[] data;
        private volatile int refCount;

        private SharedBuffer(int size) {
            data = new byte[size];
            refCount = 1;
        }
        
        synchronized void incRefCount() {
        	++refCount;
        }

        /** Decreases the reference count if it's > 1.
         *  Call this function to release your claim on this buffer if someone else is using it
         *  or hold onto the buffer if it's not shared.
         * @return true if the buffer was shared (i.e. refCount > 1 before calling this function)
         */
        synchronized boolean decRefCountIfShared() {
        	if (refCount <= 0) {
        		throw new IllegalStateException();
        	}
        	if (refCount == 1) {
        		return false;
        	}
        	--refCount;
        	return true;
        }

        public String toString(int bufSize) {
            StringBuilder result = new StringBuilder();
            result.append("refc=").append(refCount).append(" [");
            for (int i = 0; i < bufSize; i++) {
                if (i != 0) {
                    result.append(", ");
                }
                result.append(data[i]);
            }
            result.append("]");
            return result.toString();
        }
    }
    /** make short list of last allocations */
    private SharedBuffer[] buffers = new SharedBuffer[10];

    public SharedBufferCache() {
    }

    synchronized SharedBuffer newBuffer(int size) {
        int maxSize = 0;
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null && buffers[i].data.length > maxSize) {
                maxSize = buffers[i].data.length;
            }
            if (buffers[i] != null && buffers[i].data.length >= size) {
                SharedBuffer result = buffers[i];
                buffers[i] = null;         
                return result;
            }
        }        
        return new SharedBuffer(size);
    }

    /** Call this function to release your hold on the buffer (regardless of whether it's shared). */
    synchronized void release(SharedBuffer buffer) {
    	if (buffer.decRefCountIfShared()) {
    		return;
    	}
        int minPos = 0;
        int minSize = Integer.MAX_VALUE;
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] == null) {
                buffers[i] = buffer;
                return;
            } else {
                final int size = buffers[i].data.length;
                if (minSize > size) {
                    minSize = size;
                    minPos = i;
                }
            }
        }
        // replace if this is bigger buffer
        if (buffer.data.length>minSize) {
            buffers[minPos] = buffer;                
        }
    }
}
