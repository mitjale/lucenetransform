/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.lucene.store.transform.algorithm;

import java.io.IOException;

/** Null transformer, that does nothing. Used for testing and potentially CRC checking
 *
 * @author Mitja Lenič
 */
public class NullTransformer implements ReadDataTransformer, StoreDataTransformer {

    public void setConfig(byte[] pData) throws IOException {
    }

    public DataTransformer copy() {
        return this;
    }

    public int transform(byte[] inBytes, int inOffset, int inLength, byte[] outBytes, int maxOutLength) throws IOException {
        return -1;
    }

    public byte[] getConfig() throws IOException {
        return new byte[0];
    }

}
