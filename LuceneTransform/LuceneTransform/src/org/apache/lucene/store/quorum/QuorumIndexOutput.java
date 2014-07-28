/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.lucene.store.quorum;

import java.io.IOException;
import java.util.List;
import java.util.zip.CRC32;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;

/**
 *
 * @author mitja
 */
class QuorumIndexOutput extends IndexOutput {

    private final IndexOutput[] outputs;
    private final String name;
    private final IOContext ioc;
    private final boolean checkResult;
    private CRC32 crc = new CRC32();
    
    public QuorumIndexOutput(boolean checkResult, IndexOutput[] outputs, String name, IOContext ioc) {
        this.outputs = outputs;
        this.name  =name;
        this.ioc = ioc;
        this.checkResult = checkResult;
    }

    @Override
    public void flush() throws IOException {
        for (IndexOutput output : outputs) {
            output.flush();
        }
    }

    @Override
    public void close() throws IOException {
        for (IndexOutput output : outputs) {
            output.close();
        }
    }

    @Override
    public long getFilePointer() {
        if (checkResult) {
             Long result = null;
             for (IndexOutput output : outputs) {
                long lr = output.getFilePointer();
                if (result!=null && result!=lr) {
                    throw new RuntimeException("Qourum mismatch");
                }
                result = lr;
                
             }  
             return result;
        } else {
            return outputs[0].getFilePointer();
        }
    }

    

    @Override
    public long length() throws IOException {
        if (checkResult) {
             Long result = null;
             for (IndexOutput output : outputs) {
                long lr = output.length();
                if (result!=null && result!=lr) {
                    throw new RuntimeException("Qourum mismatch");
                }
                result = lr;
                
             }  
             return result;
        } else {
            return outputs[0].length();
        }
    }

    @Override
    public void writeByte(byte b) throws IOException {
          for (IndexOutput output : outputs) {
            output.writeByte(b);
            crc.update(b);
        }
  }

    @Override
    public void writeBytes(byte[] bytes, int i, int i1) throws IOException {
          for (IndexOutput output : outputs) {
            output.writeBytes(bytes,i,i1);
            crc.update(bytes,i,i1);
        }
    }

    @Override
    public long getChecksum() throws IOException {
        return crc.getValue();
    }
    
}
