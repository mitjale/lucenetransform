/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.lucene.store.transform.algorithm;

import java.io.IOException;

/** Store transformation that creates configuration for Read transformation
 *
 * @author Mitja Lenič
 */
public interface StoreDataTransformer extends DataTransformer {

    /** returns read transformer configuration, that enables reading stored chunks from this transformer.
     *
     * @return
     */
    byte[] getConfig() throws IOException;    

}
