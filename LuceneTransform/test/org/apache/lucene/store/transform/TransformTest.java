/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.apache.lucene.store.transform;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.zip.Deflater;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.transform.algorithm.NullTransformer;
import org.apache.lucene.store.transform.algorithm.ReadPipeTransformer;
import org.apache.lucene.store.transform.algorithm.StorePipeTransformer;
import org.apache.lucene.store.transform.algorithm.compress.DeflateDataTransformer;
import org.apache.lucene.store.transform.algorithm.compress.InflateDataTransformer;
import org.apache.lucene.store.transform.algorithm.security.DataDecryptor;
import org.apache.lucene.store.transform.algorithm.security.DataEncryptor;
import org.apache.lucene.util.Version;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Mitja Lenič
 */
public class TransformTest {

    private final int count = 10000;

    public TransformTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        delTree(new File("data/test"));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {        
    }

    @After
    public void tearDown() {
    }

    private void fillDoc(Document doc, int i ) {
        doc.add(new Field("id", String.valueOf(i), Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new Field("content", "Long same words to get better result "+String.valueOf(i), Field.Store.YES, Field.Index.ANALYZED));

    }

    private void TestLucene(Directory dir, int count, String testInfo) throws IOException {
        long initTime = System.currentTimeMillis();
        Analyzer anal = new StandardAnalyzer(Version.LUCENE_30);
        IndexWriter writer = new IndexWriter(dir, anal, IndexWriter.MaxFieldLength.UNLIMITED);
        for (int i = 0; i<count; i++) {
            Document doc = new Document();
            fillDoc(doc, i);
            writer.addDocument(doc);

        }
        //make split for optimization test
        writer.commit();
        for (int i = 0; i<count; i++) {
            Document doc = new Document();
            fillDoc(doc, i);
            writer.addDocument(doc);


        }
        writer.commit();
        System.out.println("Indexing "+testInfo+":"+(System.currentTimeMillis()-initTime)+" ms");
        initTime = System.currentTimeMillis();
        writer.optimize();
        System.out.println("Optimizing "+testInfo+":"+(System.currentTimeMillis()-initTime)+" ms");
        initTime = System.currentTimeMillis();        
        writer.close();
        System.out.println("Closing "+testInfo+":"+(System.currentTimeMillis()-initTime)+" ms");

        initTime = System.currentTimeMillis();
        IndexReader reader = IndexReader.open(dir, true);
        Searcher searcher = new IndexSearcher(reader);
        for (int i = 0; i<count; i++) {
            TopDocs docs = searcher.search(new TermQuery(new Term("id",String.valueOf(i))),10);
            assertTrue(docs.totalHits==2);
            Document doc1 = reader.document(docs.scoreDocs[0].doc);
            Document doc2 = reader.document(docs.scoreDocs[1].doc);
        }
        reader.close();
        System.out.println("Query "+testInfo+":"+(System.currentTimeMillis()-initTime)+" ms");

    }

    // TODO add test methods here.
    // The methods must be annotated with annotation @Test. For example:
    //
     @Test
     public void lucene() throws IOException {
         
         Directory dir = FSDirectory.open(new File("data/test/lucene"));
         TestLucene(dir, count, "lucene");
     }
     @Test
     public void luceneNull() throws IOException {

         Directory dir = FSDirectory.open(new File("data/test/nlucene"));
         Directory ndir = new TransformedDirectory(dir, new NullTransformer(), new NullTransformer());
         TestLucene(ndir, count, "lucene null");
     }

     @Test
     public void compressed() throws IOException {
         Directory bdir = FSDirectory.open(new File("data/test/clucene"));
         Directory cdir = new CompressedIndexDirectory(bdir);
         TestLucene(cdir, count, "compressed");
     }

     @Test
     public void encrypted() throws IOException, GeneralSecurityException {
         Directory bdir = FSDirectory.open(new File("data/test/elucene"));
         byte[] salt = new byte[16];
         String password = "lucenetransform";
         DataEncryptor enc = new DataEncryptor("AES", password, salt, 128,false);
         DataDecryptor dec = new DataDecryptor(password, salt,false);
         Directory cdir = new TransformedDirectory(bdir, enc, dec);
         TestLucene(cdir, count, "AES encrypted");
     }

     @Test
     public void encryptedCBC() throws IOException, GeneralSecurityException {
         Directory bdir = FSDirectory.open(new File("data/test/eelucene"));
         byte[] salt = new byte[16];
         String password = "lucenetransform";
         DataEncryptor enc = new DataEncryptor("AES/CBC/PKCS5Padding", password, salt, 128,false);
         DataDecryptor dec = new DataDecryptor(password, salt,false);
         Directory cdir = new TransformedDirectory(bdir, enc, dec);
         TestLucene(cdir, count, "AES CBC encrypted");
     }
     @Test
     public void compressedEncryptedCBC() throws IOException, GeneralSecurityException {
         Directory bdir = FSDirectory.open(new File("data/test/celucene"));
         byte[] salt = new byte[16];
         String password = "lucenetransform";
         DataEncryptor enc = new DataEncryptor("AES/CBC/PKCS5Padding", password, salt, 128,false);
         DataDecryptor dec = new DataDecryptor(password, salt,false);

         StorePipeTransformer st = new StorePipeTransformer(new DeflateDataTransformer(Deflater.BEST_COMPRESSION, 1), enc);
         ReadPipeTransformer rt = new ReadPipeTransformer(dec, new InflateDataTransformer());

         Directory cdir = new TransformedDirectory(bdir, st, rt);
         TestLucene(cdir, count, "Compressed AES CBC encrypted");
     }

     public  static void delTree(File root) {
         File files[] = root.listFiles();
         for (File f : files) {
             if (f.isDirectory()) {
                 delTree(f);
             }
             f.delete();
         }
     }

     public static void main(String args[]) throws IOException {

         TransformTest tt = new TransformTest();
         tt.luceneNull();
     }

}