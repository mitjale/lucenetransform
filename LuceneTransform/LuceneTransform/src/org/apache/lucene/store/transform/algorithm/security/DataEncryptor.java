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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.OutputStreamIndexOutput;
import org.apache.lucene.store.transform.algorithm.DataTransformer;
import org.apache.lucene.store.transform.algorithm.StoreDataTransformer;

/** Creates chunk encryptor using JCE.
 *
 * @author Mitja Lenič
 */
public class DataEncryptor implements StoreDataTransformer {

    private String algorithm;
    private String password;
    private byte[] salt;
    private int keyLength;
    private byte[] iv;
    private Cipher cipher;
    private boolean deepCopy;
    private SecretKey secret;

    public DataEncryptor(String algorithm, String password, byte[] salt, int keyLength, boolean deepCopy) throws GeneralSecurityException {
        this.algorithm = algorithm;
        this.password = password;
        this.salt = salt;
        this.keyLength = keyLength;
        this.deepCopy = deepCopy;
        initCipher();
    }

    public DataEncryptor(String password, byte[] salt, boolean deepCopy) throws GeneralSecurityException {
        this("AES/CBC/PKCS5Padding", password, salt, 128, deepCopy);
    }

    private void initCipher() throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 1024, keyLength);
        SecretKey tmp = factory.generateSecret(spec);
        secret = new SecretKeySpec(tmp.getEncoded(), algorithm.split("/")[0]);

        cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.ENCRYPT_MODE, secret);
        AlgorithmParameters params = cipher.getParameters();
        IvParameterSpec pspec = null;
        if (params != null) {
            pspec = params.getParameterSpec(IvParameterSpec.class);
        }
        if (pspec != null) {
            iv = pspec.getIV();
        } else {
            iv = new byte[0];
        }
    }

    public DataTransformer copy() {
        if (deepCopy) {
            try {
                return new DataEncryptor(algorithm, password, salt, keyLength, deepCopy);
            } catch (GeneralSecurityException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            return this;
        }
    }

    public synchronized int transform(byte[] inBytes, int inOffset, int inLength, byte[] outBytes, int maxOutLength) throws IOException {
        try {       
            if (iv.length>0) {
                cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv));
            }
            int cnt = cipher.doFinal(inBytes, inOffset, inLength, outBytes);
            return cnt;

        } catch (Exception ex) {
            throw new IOException(ex);
        }

    }

    public byte[] getConfig() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IndexOutput out = new OutputStreamIndexOutput(bos,100);
        out.writeString(algorithm);
        out.writeVInt(keyLength);
        out.writeVInt(iv.length);
        out.writeBytes(iv, iv.length);
        out.close();
        return bos.toByteArray();
    }
}
