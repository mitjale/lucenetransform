package org.apache.lucene.store.transform;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.transform.algorithm.ReadDataTransformer;
import org.apache.lucene.store.transform.algorithm.StoreDataTransformer;
import org.apache.lucene.store.transform.algorithm.compress.DeflateDataTransformer;
import org.apache.lucene.store.transform.algorithm.compress.InflateDataTransformer;

public class TransformedDirectoryLuceneTestWrapper extends TransformedDirectory {

	protected static File getTempDir() throws IOException {
		File fsDir = File.createTempFile("LuceneTransformTest", "tmp");
		boolean result = fsDir.delete(); // It's created as a file, so we have to delete it.
		assert result;
		result = fsDir.mkdir();
		assert result;
		return fsDir;
	}
	
	public TransformedDirectoryLuceneTestWrapper() throws IOException {
		super(FSDirectory.open(getTempDir()), // where we store the index
			  FSDirectory.open(getTempDir()), // temporary files, e.g. when directStore=false.  This must be different from where we store the index.
			  128 * 1024, // chunk size
			  new DeflateDataTransformer(),
			  new InflateDataTransformer(),
			  false); // When directStore is true, using ant, Lucene tests fail (starting with testDemo).
		// TODO:  Randomize parameters, just like Lucene tests do internally.
		// TODO:  Clean up these temp dirs somehow.  We can't do it when this object closes,
		//		  because the files need to stay there in case the test reopens the same dir.
		/* Running through Eclipse, without Lucene Transform, these 54 tests fail:
NOTE: reproduce with: ant test -Dtestcase=TestQueryParser -Dtestmethod=testAutoGeneratePhraseQueriesOn -Dtests.seed=1123689640206789887:240056209581169399
NOTE: reproduce with: ant test -Dtestcase=TestQueryParser -Dtestmethod=testCJK -Dtests.seed=1123689640206789887:-2267250309803809163
NOTE: reproduce with: ant test -Dtestcase=TestQueryParser -Dtestmethod=testCJKTerm -Dtests.seed=1123689640206789887:-2264572615108684431
NOTE: reproduce with: ant test -Dtestcase=TestQueryParser -Dtestmethod=testSimple -Dtests.seed=1123689640206789887:463351417036080757
NOTE: reproduce with: ant test -Dtestcase=TestQueryParser -Dtestmethod=testCJKBoostedTerm -Dtests.seed=1123689640206789887:3551590248902905615
NOTE: reproduce with: ant test -Dtestcase=TestQueryParser -Dtestmethod=testCJKPhrase -Dtests.seed=1123689640206789887:2118306409341024572
NOTE: reproduce with: ant test -Dtestcase=TestQueryParser -Dtestmethod=testCJKBoostedPhrase -Dtests.seed=1123689640206789887:3293229821514987247
NOTE: reproduce with: ant test -Dtestcase=TestQueryParser -Dtestmethod=testCJKSloppyPhrase -Dtests.seed=1123689640206789887:6489397749932665983
NOTE: reproduce with: ant test -Dtestcase=TestASCIIFoldingFilter -Dtestmethod=testLatin1Accents -Dtests.seed=3845795303109943198:-3013496035412048012
NOTE: reproduce with: ant test -Dtestcase=TestASCIIFoldingFilter -Dtestmethod=testAllFoldings -Dtests.seed=3845795303109943198:2747201893750811332
NOTE: reproduce with: ant test -Dtestcase=TestCheckIndex -Dtestmethod=testLuceneConstantVersion -Dtests.seed=-7485824404904116995:-6091224896336597243
NOTE: reproduce with: ant test -Dtestcase=TestCollationKeyFilter -Dtestmethod=testCollationKeySort -Dtests.seed=-8852284621125539617:2790345123029206113
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testArmenian -Dtests.seed=8172681577413849397:76805142690438591
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testAmharic -Dtests.seed=8172681577413849397:-4146297799526891567
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testArabic -Dtests.seed=8172681577413849397:-4349910594896192913
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testAramaic -Dtests.seed=8172681577413849397:-232304152237948499
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testBengali -Dtests.seed=8172681577413849397:-7688304683466286925
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testFarsi -Dtests.seed=8172681577413849397:7589018532156120568
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testGreek -Dtests.seed=8172681577413849397:2223509873461097244
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testThai -Dtests.seed=8172681577413849397:4819413860567936496
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testLao -Dtests.seed=8172681577413849397:620093441963755604
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testTibetan -Dtests.seed=8172681577413849397:2675627529925996041
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testChinese -Dtests.seed=8172681577413849397:6677622956719788729
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testKoreanSA -Dtests.seed=8172681577413849397:-5712453823603267504
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testKorean -Dtests.seed=8172681577413849397:-4123249887091916476
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testJapanese -Dtests.seed=8172681577413849397:-2293424321618496836
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testLUCENE1545 -Dtests.seed=8172681577413849397:6680781944844938561
NOTE: reproduce with: ant test -Dtestcase=TestUAX29URLEmailTokenizer -Dtestmethod=testSupplementary -Dtests.seed=8172681577413849397:551742598415021018
NOTE: reproduce with: ant test -Dtestcase=TestIndexWriterExceptions -Dtestmethod=testExceptionOnMergeInit -Dtests.seed=6610568508703261490:3790037855485951877
NOTE: reproduce with: ant test -Dtestcase=TestIndexWriterExceptions -Dtestmethod=testRollbackExceptionHang -Dtests.seed=6610568508703261490:-5652498361042600097
NOTE: reproduce with: ant test -Dtestcase=TestIndexWriterExceptions -Dtestmethod=testExceptionDocumentsWriterInit -Dtests.seed=6610568508703261490:-1314653093594108574
NOTE: reproduce with: ant test -Dtestcase=TestClassicAnalyzer -Dtestmethod=testKorean -Dtests.seed=5822941344847885150:1872156130006609470
NOTE: reproduce with: ant test -Dtestcase=TestISOLatin1AccentFilter -Dtestmethod=testU -Dtests.seed=-3870568835853120637:-7729351415622546707
NOTE: reproduce with: ant test -Dtestcase=TestSort -Dtestmethod=testInternationalMultiSearcherSort -Dtests.seed=4646045703454570327:1458923422789322881
NOTE: reproduce with: ant test -Dtestcase=TestSort -Dtestmethod=testInternationalSort -Dtests.seed=4646045703454570327:8443452696437711999
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testArmenian -Dtests.seed=5920441815997043181:-1884075651734489353
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testAmharic -Dtests.seed=5920441815997043181:4262802623034729712
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testArabic -Dtests.seed=5920441815997043181:-1272786184455096194
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testAramaic -Dtests.seed=5920441815997043181:-5147553886291825624
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testBengali -Dtests.seed=5920441815997043181:-7919626480102751439
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testFarsi -Dtests.seed=5920441815997043181:-7688459470280534264
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testGreek -Dtests.seed=5920441815997043181:5978938927291395406
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testThai -Dtests.seed=5920441815997043181:2293683023721433482
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testLao -Dtests.seed=5920441815997043181:-4474207035943973349
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testTibetan -Dtests.seed=5920441815997043181:-3157451131166277838
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testChinese -Dtests.seed=5920441815997043181:8634571501735881686
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testKoreanSA -Dtests.seed=5920441815997043181:-3287095164450821622
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testKorean -Dtests.seed=5920441815997043181:2198199656692930331
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testJapanese -Dtests.seed=5920441815997043181:-2392450784639120572
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testLUCENE1545 -Dtests.seed=5920441815997043181:-2550796415148465819
NOTE: reproduce with: ant test -Dtestcase=TestStandardAnalyzer -Dtestmethod=testSupplementary -Dtests.seed=5920441815997043181:-5963537565893395617
NOTE: reproduce with: ant test -Dtestcase=TestNLS -Dtestmethod=testMessageLoading_ja -Dtests.seed=-6114076232039510383:-4040587671230258459
NOTE: reproduce with: ant test -Dtestcase=TestNLS -Dtestmethod=testNLSLoading_ja -Dtests.seed=-6114076232039510383:1336705549722357501
NOTE: reproduce with: ant test -Dtestcase=TestCollationKeyAnalyzer -Dtestmethod=testCollationKeySort -Dtests.seed=115265999109786522:7496813270657902044
		 * 
		 */
	}
/*
	public TransformedDirectoryLuceneTestWrapper(Directory nested,
			StoreDataTransformer storeTransformer,
			ReadDataTransformer readTransformer) {
		super(nested, storeTransformer, readTransformer);
		// TODO Auto-generated constructor stub
	}

	public TransformedDirectoryLuceneTestWrapper(Directory nested,
			StoreDataTransformer storeTransformer,
			ReadDataTransformer readTransformer, boolean directStore) {
		super(nested, storeTransformer, readTransformer, directStore);
		// TODO Auto-generated constructor stub
	}

	public TransformedDirectoryLuceneTestWrapper(Directory nested,
			int chunkSize, StoreDataTransformer storeTransformer,
			ReadDataTransformer readTransformer, boolean directStore) {
		super(nested, chunkSize, storeTransformer, readTransformer, directStore);
		// TODO Auto-generated constructor stub
	}

	public TransformedDirectoryLuceneTestWrapper(Directory nested,
			Directory tempDir, int chunkSize,
			StoreDataTransformer storeTransformer,
			ReadDataTransformer readTransformer, boolean directStore) {
		super(nested, tempDir, chunkSize, storeTransformer, readTransformer,
				directStore);
		// TODO Auto-generated constructor stub
	}
*/
}
