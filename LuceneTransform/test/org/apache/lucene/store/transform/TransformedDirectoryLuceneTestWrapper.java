package org.apache.lucene.store.transform;

import java.io.File;
import java.io.IOException;

import org.apache.lucene.store.FSDirectory;
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
	}
}
