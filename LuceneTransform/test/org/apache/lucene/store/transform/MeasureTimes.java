/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.lucene.store.transform;

import java.io.IOException;
import org.junit.Test;

/**
 *
 * @author mile4386
 */
public class MeasureTimes {

    @Test
    public void batchTest() throws Exception {
        singleBatchTest(128 * 1024, true);
        singleBatchTest(16 * 1024, true);
        singleBatchTest(4 * 1024, true);
        singleBatchTest(128 * 1024, false);
        singleBatchTest(16 * 1024, false);
        singleBatchTest(4 * 1024, false);
    }

    public static void main(String args[]) throws IOException, Exception {

        MeasureTimes t = new MeasureTimes();
        t.batchTest();
    }

    private void singleBatchTest(int chunkSize, boolean directStore) throws Exception {
        TransformTest tt = new TransformTest();
        tt.setChunkSize(chunkSize);
        tt.setDirectStore(directStore);
        for (int i = 0; i < 31; i++) {
            System.out.println("Run cs=" + (chunkSize / 1024) + "k directStore=" + directStore + " run=" + i);
            tt.setUp();
            tt.lucene();
            tt.setUp();
            tt.luceneNull();
            tt.setUp();
            tt.compressed();
            tt.setUp();
            tt.encrypted();
            tt.setUp();
            tt.encryptedCBC();
            tt.setUp();
            tt.compressedEncryptedCBC();
            if (i == 0) {
                // clear statistics to prevent startup influence
                tt.getStatistics().clear();
            } else {
                System.out.println(tt.getStatistics());
            }
        }
        System.out.println(tt.getStatistics());
    }
}
