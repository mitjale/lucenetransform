/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.lucene.store.transform;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
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
    private final int searchCount = 10000;
    private TimeCollector times;
    private int chunkSize = 128 * 1024;
    private boolean directStore = true;

    public TransformTest() {
        times = new TimeCollector();
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() throws InterruptedException {
        delTree(new File("data/test"));
        //allow OS FS layer to cleanup

      //  Thread.sleep(10000);
    }

    @After
    public void tearDown() {
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public boolean isDirectStore() {
        return directStore;
    }

    public void setDirectStore(boolean directStore) {
        this.directStore = directStore;
    }

    private void logTime(String method, String ops, long time) {
        times.addMesurement(method, ops, time);
    }

    private void TestLucene(Directory dir, int count, String testInfo, File fdir) throws IOException {
        long initTime = System.currentTimeMillis();
        Analyzer anal = new StandardAnalyzer(Version.LUCENE_30);
        IndexWriter writer = new IndexWriter(dir, anal, IndexWriter.MaxFieldLength.UNLIMITED);
        //writer.setUseCompoundFile(false);
        logTime(testInfo, "WriterOpen(ms)", System.currentTimeMillis() - initTime);
        initTime = System.currentTimeMillis();
        Document doc = new Document();
        Field id = new Field("id", "", Field.Store.YES, Field.Index.NOT_ANALYZED);
        Field content = new Field("content", "", Field.Store.YES, Field.Index.ANALYZED);
        doc.add(id);
        doc.add(content);
        for (int j = 0; j < 2; j++) {

            for (int i = 0; i < count; i++) {
                id.setValue(String.valueOf(i));
                content.setValue("Long same words to get better result " + String.valueOf(i));
                writer.addDocument(doc);

            }

            //make split for optimization test
            writer.commit();
        }
        logTime(testInfo, "Indexing(ms)", System.currentTimeMillis() - initTime);
        initTime = System.currentTimeMillis();
        writer.optimize();
        logTime(testInfo, "Optimization(ms)", System.currentTimeMillis() - initTime);
        initTime = System.currentTimeMillis();
        writer.close();
        logTime(testInfo, "Closing(ms)", System.currentTimeMillis() - initTime);

        initTime = System.currentTimeMillis();
        IndexReader reader = IndexReader.open(dir, true);
        logTime(testInfo, "ReaderOpen(ms)", System.currentTimeMillis() - initTime);
        initTime = System.currentTimeMillis();
        Searcher searcher = new IndexSearcher(reader);
        TopDocs sdocs = searcher.search(new TermQuery(new Term("id", String.valueOf(count / 2))), 10);
        logTime(testInfo, "Single search(ms)", System.currentTimeMillis() - initTime);
        assertTrue(sdocs.totalHits == 2);
        initTime = System.currentTimeMillis();
        for (int i = 0; i < searchCount; i++) {
            TopDocs docs = searcher.search(new TermQuery(new Term("id", String.valueOf(i))), 10);
            assertTrue(docs.totalHits == 2);
            Document doc1 = reader.document(docs.scoreDocs[0].doc);
            Document doc2 = reader.document(docs.scoreDocs[1].doc);
        }
        logTime(testInfo, "Search(ms)", System.currentTimeMillis() - initTime);
        initTime = System.currentTimeMillis();
        reader.close();
        logTime(testInfo, "ReaderClose(ms)", System.currentTimeMillis() - initTime);
        dir.close();
        long totals = du(fdir);
        times.addMesurement(testInfo, "Size(bytes)", totals);

    }

    // TODO add test methods here.
    // The methods must be annotated with annotation @Test. For example:
    //
    @Test
    public void lucene() throws IOException {
        final File ldir = new File("data/test/lucene");
        Directory dir = FSDirectory.open(ldir);
        TestLucene(dir, count, "lucene", ldir);
    }

    @Test
    public void luceneNull() throws IOException {
        final File ldir = new File("data/test/nlucene");
        Directory dir = FSDirectory.open(ldir);
        Directory ndir = new TransformedDirectory(dir, chunkSize, new NullTransformer(), new NullTransformer(), directStore);
        TestLucene(ndir, count, "lucene null", ldir);
    }

    @Test
    public void compressed() throws IOException {
        final File dir = new File("data/test/clucene");
        Directory bdir = FSDirectory.open(dir);
        //Directory cdir = new CompressedIndexDirectory(bdir);
        Directory cdir = new TransformedDirectory(bdir, chunkSize, new DeflateDataTransformer(), new InflateDataTransformer(), directStore);
        TestLucene(cdir, count, "compressed", dir);
    }

    @Test
    public void encrypted() throws IOException, GeneralSecurityException {
        final File dir = new File("data/test/elucene");
        Directory bdir = FSDirectory.open(dir);
        byte[] salt = new byte[16];
        String password = "lucenetransform";
        DataEncryptor enc = new DataEncryptor("AES", password, salt, 128, false);
        DataDecryptor dec = new DataDecryptor(password, salt, false);
        Directory cdir = new TransformedDirectory(bdir, chunkSize, enc, dec, directStore);
        TestLucene(cdir, count, "AES encrypted", dir);
    }

    @Test
    public void encryptedCBC() throws IOException, GeneralSecurityException {
        final File dir = new File("data/test/eelucene");
        Directory bdir = FSDirectory.open(dir);
        byte[] salt = new byte[16];
        String password = "lucenetransform";
        DataEncryptor enc = new DataEncryptor("AES/CBC/PKCS5Padding", password, salt, 128, false);
        DataDecryptor dec = new DataDecryptor(password, salt, false);
        Directory cdir = new TransformedDirectory(bdir, chunkSize, enc, dec, directStore);
        TestLucene(cdir, count, "AES CBC encrypted", dir);
    }

    @Test
    public void compressedEncryptedCBC() throws IOException, GeneralSecurityException {
        final File dir = new File("data/test/celucene");
        Directory bdir = FSDirectory.open(dir);
        byte[] salt = new byte[16];
        String password = "lucenetransform";
        DataEncryptor enc = new DataEncryptor("AES/CBC/PKCS5Padding", password, salt, 128, false);
        DataDecryptor dec = new DataDecryptor(password, salt, false);

        StorePipeTransformer st = new StorePipeTransformer(new DeflateDataTransformer(Deflater.BEST_COMPRESSION, 1), enc);
        ReadPipeTransformer rt = new ReadPipeTransformer(dec, new InflateDataTransformer());

        Directory cdir = new TransformedDirectory(bdir, chunkSize, st, rt, directStore);
        TestLucene(cdir, count, "Compressed AES CBC encrypted", dir);
    }

    public static void delTree(File root) {
        File files[] = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    delTree(f);
                }
                f.delete();
            }
        }
    }

    public static long du(File root) {
        long size = 0;
        File files[] = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    size += du(f);
                }
                if (f.isFile()) {
                    size += f.length();
                }
            }
        }
        return size;
    }

    public TimeCollector getStatistics() {
        return times;
    }
}
