/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.lucene.store.transform;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexDeletionPolicy;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.MergePolicy.OneMerge;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.transform.algorithm.security.DataDecryptor;
import org.apache.lucene.store.transform.algorithm.security.DataEncryptor;
import org.apache.lucene.util.Version;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author mitja
 */
public class CryptoTest {

    private final String path = "/tmp/test";
    private Directory encryptedDirectory;
    private byte[] salt;
    private String password;

    public CryptoTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    private void openDirectory() throws IOException {
        Directory directory = FSDirectory.open(new File(path));
        DataEncryptor enc;
        try {
            enc = new DataEncryptor("AES/ECB/PKCS5Padding", password, salt, 128, false);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
        DataDecryptor dec = new DataDecryptor(password, salt, false);
        encryptedDirectory = new TransformedDirectory(directory, 1024, enc, dec,true);

    }

    @Before
    public void setUp() throws IOException {
        salt = new byte[16];
        password = "lucenePa$$";
        openDirectory();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testCloseReopen() throws IOException {
        openDirectory();
        randomFill(1000000);
        encryptedDirectory.close();
        openDirectory();
        randomFill(1000000);
        encryptedDirectory.close();
        
        
    }

    private void randomFill(int cnt) throws IOException {
        IndexWriterConfig cfg =new IndexWriterConfig(Version.LUCENE_30,new StandardAnalyzer(Version.LUCENE_30));
        IndexWriter writer = new IndexWriter(encryptedDirectory,cfg);
        Document doc = new Document();
        Field id = new Field("id", "", Field.Store.YES, Field.Index.ANALYZED);
        Field vals[] = new Field[100];
        for (int j=0;j<vals.length;j++ )  {
          vals[j]= new Field("val", "", Field.Store.YES, Field.Index.ANALYZED);
        } 
        for (int i = 0; i<cnt; i++) {
            id.setValue(String.valueOf(i));;
            for (int j=0;j<vals.length;j++ )  {
              vals[j].setValue("x"+i+"_"+j);
            } 
            
            writer.addDocument(doc);
                    
        }
        writer.optimize();
        writer.close();
        
        
       
    }
}
