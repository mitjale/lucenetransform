/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.lucene.store.quorum;

import java.io.IOException;
import java.util.Collection;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockFactory;

/**
 *
 * @author mitja
 */
public class QuorumDirectory extends Directory {

    private final Directory directories[];
    private final boolean checkResult;

    public QuorumDirectory(boolean checkResult, Directory... direcory) {
        this.directories = direcory;
        this.checkResult = checkResult;
    }

    @Override
    public String[] listAll() throws IOException {
        return directories[0].listAll();
    }

    @Override
    public boolean fileExists(String name) throws IOException {
        Boolean result = null;
        for (Directory directory : directories) {
            boolean ln = directory.fileExists(name);
            if (checkResult) {
                if (result != null) {
                    if (ln != result) {
                        throw new IOException("Quorum mismasch");
                    }
                } else {
                    result = ln;
                }
            } else {
                return ln;
            }
        }
        return result;
    }

    @Override
    public void deleteFile(String name) throws IOException {
        for (Directory directory : directories) {
            directory.deleteFile(name);
        }

    }

    @Override
    public long fileLength(String name) throws IOException {
        Long result = null;
        for (Directory directory : directories) {
            long ln = directory.fileLength(name);
            if (checkResult) {
                if (result != null) {
                    if (ln != result) {
                        throw new IOException("Quorum mismasch");
                    }
                } else {
                    result = ln;
                }
            } else {
                return ln;
            }
        }
        return result;
    }

    @Override
    public IndexOutput createOutput(String name, IOContext ioc) throws IOException {
        IndexOutput outputs[] = new IndexOutput[directories.length];
        for (int i = 0; i < directories.length; i++) {
            Directory directory = directories[i];
            outputs[i] = directory.createOutput(name, ioc);
        }
        return new QuorumIndexOutput(checkResult, outputs, name, ioc);
    }

    @Override
    public void sync(Collection<String> names) throws IOException {
        for (Directory directory : directories) {
            directory.sync(names);
        }
    }

    @Override
    public IndexInput openInput(String name, IOContext ioc) throws IOException {
        IndexInput inputs[] = new IndexInput[directories.length];
        for (int i = 0; i < directories.length; i++) {
            Directory directory = directories[i];
            inputs[i] = directory.openInput(name, ioc);
        }
        return new QuorumIndexInput(checkResult, inputs, name, ioc);
    }

    @Override
    public void close() throws IOException {
        for (Directory directory : directories) {
            directory.close();
        }

    }

    @Override
    public void clearLock(String name) throws IOException {
        for (Directory directory : directories) {
            directory.clearLock(name);
        }
    }

    @Override
    public LockFactory getLockFactory() {
       return directories[0].getLockFactory();
    }

    @Override
    public String getLockID() {
        return directories[0].getLockID();
    }

    @Override
    public Lock makeLock(String name) {
        Lock result = null;
        for (int i =directories.length-1; i>=0;i--) {
            Directory directory = directories[i];
            result = directory.makeLock(name);
        }
        return result;
    }

    @Override
    public void setLockFactory(LockFactory lockFactory) throws IOException {
       for (Directory directory : directories) {
            directory.setLockFactory(lockFactory);
        }
    }
}
