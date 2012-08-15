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
package org.apache.lucene.store.transform.algorithm.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.transform.ByteIndexInput;
import org.apache.lucene.store.transform.algorithm.DataTransformer;
import org.apache.lucene.store.transform.algorithm.ReadDataTransformer;

/** Decryption algorithm based on password with the same algorithm as DataEncryptor
 *
 * @see DataEncryptor
 * @author Mitja Lenič
 */
public class DataDecryptor implements ReadDataTransformer {

    private String algorithm;
    private String password;
    private byte[] salt;
    private int keyLength;
    private byte[] iv;
    private Cipher cipher;
    private boolean deepCopy;
    private SecretKey secret;

    public DataDecryptor(String password, byte[] salt, boolean deepCopy) {
        this.password = password;
        this.salt = salt;
        this.deepCopy = deepCopy;
    }

    private void initCipher() throws GeneralSecurityException {

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 1024, keyLength);
        SecretKey tmp = factory.generateSecret(spec);
        secret = new SecretKeySpec(tmp.getEncoded(), algorithm.split("/")[0]);
        cipher = Cipher.getInstance(algorithm);
        if (iv.length > 0) {
            cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
        } else {
            cipher.init(Cipher.DECRYPT_MODE, secret);
        }        

    }
    
    public  synchronized void setConfig(byte[] pData) throws IOException {
        IndexInput input = new ByteIndexInput("config",pData);
        algorithm = input.readString();
        keyLength = input.readVInt();
        int ivlen = input.readVInt();
        iv = new byte[ivlen];
        input.readBytes(iv, 0, ivlen);
        input.close();
        try {
            initCipher();
        } catch (GeneralSecurityException ex) {
            throw new IOException(ex);
        }
    }

    public DataTransformer copy() {
        if (this.deepCopy) {
            DataDecryptor dec = new DataDecryptor(password, salt, deepCopy);
            dec.algorithm = algorithm;
            if (iv != null) {
                dec.iv = iv.clone();
            }
            dec.keyLength = keyLength;
            try {
                if (algorithm != null) {
                    dec.initCipher();
                } else {
                    throw new IllegalStateException("Cipher algorithm not specified");
                }
            } catch (GeneralSecurityException ex) {
                throw new RuntimeException(ex);
            }
            return dec;
        } else {
            return this;
        }
    }

    public synchronized int transform(byte[] inBytes, int inOffset, int inLength, byte[] outBytes, int maxOutLength) throws IOException {
        try {
              if (iv.length>0) {
                cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
            }
            int cnt = cipher.doFinal(inBytes, inOffset, inLength, outBytes);
            return cnt;

        } catch (Exception ex) {
            throw new IOException(ex);
        }

    }
}
