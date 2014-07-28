/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.lucene.store.quorum;

import java.io.IOException;
import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;

/**
 *
 * @author mitja
 */
class QuorumIndexInput extends IndexInput {

    private  IndexInput inputs[];
    private final String name;
    private final IOContext ioc;
    private final boolean checkResult;
    private long offset;
    private long maxLen = -1;

    public QuorumIndexInput(boolean checkResult, IndexInput[] inputs, String name, IOContext ioc) {
        super(name);
        this.inputs = inputs;
        this.name = name;
        this.ioc = ioc;
        this.checkResult = checkResult;
    }

    @Override
    public void close() throws IOException {
        for (IndexInput input : inputs) {
            input.close();
        }
    }
    
    @Override
    public long getFilePointer() {
        if (checkResult) {
            Long result = null;
            for (IndexInput input : inputs) {
                long lr = input.getFilePointer();
                if (result != null && result != lr) {
                    throw new RuntimeException("Qourum mismatch");
                }
                result = lr;

            }
            return result-offset;
        } else {
            return inputs[0].getFilePointer()-offset;
        }
    }

    @Override
    public void seek(long l) throws IOException {
        for (IndexInput input : inputs) {
            input.seek(l+offset);
        }
    }

    @Override
    public long length() {
        if (maxLen>=0) {
            return maxLen;
        }
        if (checkResult) {
            Long result = null;
            for (IndexInput input : inputs) {
                long lr = input.length();
                if (result != null && result != lr) {
                    throw new RuntimeException("Qourum mismatch");
                }
                result = lr;

            }
            return result;
        } else {
            return inputs[0].length();
        }
    }

    @Override
    public byte readByte() throws IOException {
        if (checkResult) {
            Byte result = null;
            for (IndexInput input : inputs) {
                byte lr = input.readByte();
                if (result != null && result != lr) {
                       System.out.println("readBytes Qourum mismatch");
                    throw new RuntimeException("Qourum mismatch");
                }
                result = lr;

            }
            return result;
        } else {
            return inputs[0].readByte();
        }
    }

    @Override
    public void readBytes(byte[] bytes, int i, int i1) throws IOException {
       if (checkResult) {
            byte[] ocopy = (byte[])bytes.clone();
            inputs[0].readBytes(bytes,i,i1);
            for (int p = 1; p<inputs.length;p++) {
               byte[] copy = (byte[])ocopy.clone();
               inputs[p].readBytes(copy,i,i1);
               for (int j =i; j<i+i1; j++) {
                   if (bytes[j]!=copy[j]) {
                       System.out.println("readBytes Qourum mismatch");
                    throw new RuntimeException("Qourum mismatch");
                      
                   }
               }     
            }            
        } else {
            inputs[0].readBytes(bytes,i,i1);
        }
    }
    
     @Override
    public IndexInput clone() {
        QuorumIndexInput clone = (QuorumIndexInput) super.clone();
        clone.inputs = inputs.clone();
        for (int i = 0; i<inputs.length;i++) {
            clone.inputs[i] = (IndexInput) inputs[i].clone();
        }
        return clone;
    }

    @Override
    public IndexInput slice(String string, long l, long l1) throws IOException {
        QuorumIndexInput slice = (QuorumIndexInput) this.clone();
        slice.offset = l;
        slice.maxLen = l1;
        slice.seek(0);
        return slice;
    }
    
}
