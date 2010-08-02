/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.lucene.store.transform;

import java.io.IOException;

/**
 *
 * @author mile4386
 */
public class MeasureTimes {
      public static void main(String args[]) throws IOException, Exception {

         TransformTest tt = new TransformTest();
         for (int i = 0; i<110; i++) {
             System.out.println("Run "+i);
            tt.setUpClass();
            tt.lucene();
            tt.luceneNull();
            tt.compressed();
            tt.encrypted();
            tt.encryptedCBC();
            tt.compressedEncryptedCBC();
            if (i==0) {
                // clear statistics to prevent startup influence
                tt.getStatistics().clear();
            }
         }
         System.out.println(tt.getStatistics());

     }

}
