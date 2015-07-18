# Quick guide #

LuceneTransform implements transparent transformation (most notably compression) of lucene directory index by using decorator design pattern. Before opening index, an underlaying [Directory](http://lucene.apache.org/java/2_2_0/api/org/apache/lucene/store/Directory.html) must be created, that stores compressed/transformed data.

## Using compressed directory ##
Here is the example how to open compressed index

```
         Directory bdir = FSDirectory.open(new File("data/test/clucene"));
         Directory cdir = new CompressedIndexDirectory(bdir);
```

After successful open, IndexWriter/IndexReader can be created:

```
IndexWriter writer = new IndexWriter(cdir, ..... );


IndexReader reader = new IndexReader(cdir, ......);
```


## Encrypted directory ##

For encrypted directory JCE encryption is used. To open encrypted directory password and random seed must be specified

```
         Directory bdir = FSDirectory.open(new File("data/test/eelucene"));
         byte[] salt = new byte[16];
         String password = "lucenetransform";
         DataEncryptor enc = new DataEncryptor("AES/ECB/PKCS5Padding", password, salt, 128,false);
         DataDecryptor dec = new DataDecryptor(password, salt,false);
         Directory cdir = new TransformedDirectory(bdir, enc, dec);
```

## Combined transformation ##
Example demonstrates how to create compressed and encrypted directory:

```
         Directory bdir = FSDirectory.open(new File("data/test/celucene"));
         byte[] salt = new byte[16];
         String password = "lucenetransform";
         DataEncryptor enc = new DataEncryptor("AES/ECB/PKCS5Padding", password, salt, 128,false);
         DataDecryptor dec = new DataDecryptor(password, salt,false);

         StorePipeTransformer st = new StorePipeTransformer(new DeflateDataTransformer(Deflater.BEST_COMPRESSION, 1), enc);
         ReadPipeTransformer rt = new ReadPipeTransformer(dec, new InflateDataTransformer());

         Directory cdir = new TransformedDirectory(bdir, st, rt);
```

## Important notes ##
It is imperative, that directory used for IndexWriter and IndexReader is constructed with same algorithms, otherwise index will not be readable. ReadDataTrasformer must be inverse function of StoreDataTransformer, for every input, otherwise data will be lost.

Opening IndexReader and IndexWriter on the same directory simultaneously can have unpredictable results. If you need to use IndexReader when indexing, use new Lucene API  method IndexWriter.getReader();