# Introduction #

In Lucene compression can be achieved by compressing fields. The problem is, that those fields are not indexed and thus not searchable. Big part of index consists  of words (text),that can be easily compressed. Therefore compression has to be implemented on
storage (directory) level, where whole index can be compressed.

This library utilizes pluggable architecture of Lucene directory and creates transparent layer, that enables compression and as side result also other transformations.

## Compression ##
The main problem with compression is, that size (and position) of the data changes, thus changing data requires reallocation (re-compression) of all subsequent data. To avoid this problem file is sliced into fixed sized chunks. Each chunk has information about original position, compressed (transformed) size, original size, and for safety CRC of original data.

Chunks are always appended (no overwrite). If there is seek operation on already written position, chunk is appended with starting position of seek, thereby generating log structured file (only append). Chunk can be smaller than fixed size, if after write operation, seek operation on other location is issued or if chunk is last chunk of the file.

When reading data, every location (chunk) is checked for possible overwrites and merged into final data content of file.

Chunked files contains chunk directory located at the end of file that is always loaded in memory (due to required search operations for seek positions and overwrites). If chunk directory is missing (eg. interrupted write), there is enough information in chunks to rebuild chunk directory by scanning the file.

## General transformation ##
If this approach works for compression, than it is very simple to replace compression algorithm with other algorithms eg. encryption. It is only important that transformation has fully and unambiguously defined inverse transformation.

Typically, transformations do have some parameters, like for example, when using compression, it can be applied multiple times. This information is stored in config section of chuncked file.