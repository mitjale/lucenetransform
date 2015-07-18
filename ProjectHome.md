Apache Lucene is a high-performance, full-featured text search engine library written entirely in Java.

This library provides driver for transparent transformation (eg. compression, encryption) of Lucene directory, thereby enabling space efficient and safe (encrypted) storage of sensitive data in Lucene index.

Main features:
  * index files are sliced into transformable chunks,
  * pluggable algorithms for chunk processing/transformation,
  * chunk CRC calculation,
  * transparent, decorated directory,
  * enables transformation processing on file close for high performance indexing

Most prominent feature is index compression. For compression algorithm java deflate implementation is used. Expected compression ratio on textual data can be up to 1:10.

[Examples](Examples.md)

[Implementation](Implementation.md)

[Performance](Performance.md)