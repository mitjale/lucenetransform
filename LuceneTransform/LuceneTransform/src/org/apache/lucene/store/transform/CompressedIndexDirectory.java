/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.store.transform;

import java.io.File;
import java.io.IOException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.transform.algorithm.compress.DeflateDataTransformer;
import org.apache.lucene.store.transform.algorithm.compress.InflateDataTransformer;
import org.apache.lucene.store.FSDirectory;
/** Convenience class for compressed index directory.
 *
 * @author Mitja Lenič
 */
public class CompressedIndexDirectory extends TransformedDirectory {

    public CompressedIndexDirectory(Directory nested, Directory tempDir, int chunkSize, boolean directStore) {
        super(nested, tempDir, chunkSize, new DeflateDataTransformer(), new InflateDataTransformer(), directStore);
    }

    public CompressedIndexDirectory(Directory nested, int chunkSize, boolean directCompression) {
        super(nested, chunkSize, new DeflateDataTransformer(), new InflateDataTransformer(), directCompression);
    }

    public CompressedIndexDirectory(Directory nested, boolean directCompression) {
        super(nested, new DeflateDataTransformer(), new InflateDataTransformer(), directCompression);
    }

    public CompressedIndexDirectory(Directory nested) {
        super(nested, new DeflateDataTransformer(), new InflateDataTransformer());
    }

    public CompressedIndexDirectory(File pDirectory) throws IOException {
        this(FSDirectory.open(pDirectory));        
    }

}
