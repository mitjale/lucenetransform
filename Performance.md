# Performance #
Performance is important with Lucene. Performance impact of the library can vary, dependent on your infrastructure and data.

There are some parameters, that have great impact:
  * chunk size
  * use of sequential log based writing,
  * transformation algorithms

By default block site is set to 128KB and sequential log based writing. This settings are optimized (when used with compression) for good compression.



## Simple benchmark ##

I've made small benchmark based on test case. For each method 20000 documents are inserted into index. Every 10000 documents are separately committed to create multiple segments, then index is optimized  and searched 10000 times. Search results (always two) are retrieved. All measurements are in milliseconds, except size, which is in bytes.

Method description:
  * lucene - lucene without transformation directory
  * lucene null - transformation directory with null transform, eg. just creating chunks
  * compress - transparent compression (size should be smaller)
  * AES encrypted - transparent encryption with AES (128-bits)
  * AES CBC encrypted - transparent encryption with AES CBC (128-bits)
  * Compressed AES CBC encrypted - transparent compression with AES CBC (128-bits) encryption

All operations were repeated 31 times. Data from first run is omitted from statistics. All files were created on ramfs filesystem. There is also [test](large_test.md) with real disk IO and 4000000 documents.

### Notes ###
Size can vary of chunked index can vary, dependent on content of data, since CRC is written with writeVLong().

Bulk searching for 10000 times and loading documents, spends most of the time in memory allocation operations (garbage collection), due to nature of lucene file cloning (eg. allocating and freeing copy of all chunk buffers). Therefore smaller buffer size yields in better search performance, which is not necessary true for real world load.

The purpose of this benchmark is to show possible impact of various parameters. For optimal results, create tests on expected load.



## Transformation after close ##
It is possible to trigger transformation after directory file close. Temporary directory files are created without any application of transformation (eg. unencrypted/uncompresed) without chunking. After close operation on file is called, whole file is transformed. File close operations are also called from lucene indexer, therefore transformation can be applied during indexing.

This mode guaranties best read performance, since no chunk is overwritten.

In general log based writing and transformation after close approaches do not differ a lot. When using compound file, log based approach has only few overwritten chunks.

If it is allowed and able (size/disk speed limitations) to write untransformed data, it is recommended to use log based approach, since most of complicated logic about merging chunks (fast) is not used when reading.

As expected, chunk size has great impact. Example of performance ratio, relative to FSDirectory, when using compression log based writing and compound file:

|Measurement/chunk size|  4k | 16k | 128k |
|:---------------------|:----|:----|:-----|
| Size                 | 11,12%+/-0,00%|10,22%+/-0,00%|10,24%+/-0,00%|
| Search time          |138,25%+/-12,32%|201,08%+/-14,57%|627,96%+/-74,86%|
| Indexing time        |145,09%+/-8,89%|141,39%+/-8,60%|152,81%+/-10,67%|
| Index optimization time|234,24%+/-16,51%|220,54%+/-32,40%|258,95%+/-16,19%|

See [detailed measurements](Performance_detail.md) for all comparisons.

# Performance Tips #
If indexing performance is very important, you can index directly without transformation. After index is closed, content of Lucene directory can be copied from untransformed to transformed index with Lucene's Directory.copy class method.

Small chunk sizes should be avoided on large indexes, due to large number of needed chunk directory entries that are loaded and kept in memory. There is also the limit of maximum number of chunks, that is limited with maximum array size 2^31-1.

If performance is important, don't use compound file format.