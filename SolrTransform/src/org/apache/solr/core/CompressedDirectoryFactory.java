/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.solr.core;

import java.io.IOException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.transform.CompressedIndexDirectory;

/**
 *
 * @author mitja
 */
public class CompressedDirectoryFactory extends StandardDirectoryFactory {

    @Override
    protected Directory create(String path, DirContext dirContext) throws IOException {
        Directory nested = super.create(path, dirContext); 
        return new CompressedIndexDirectory(nested, 4096, true);
    }
    
}
