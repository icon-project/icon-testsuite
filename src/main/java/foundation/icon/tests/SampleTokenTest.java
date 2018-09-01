/*
 * Copyright (c) 2018 ICON Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package foundation.icon.tests;

import foundation.icon.icx.*;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import org.web3j.crypto.CipherException;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

public class SampleTokenTest {
    private final String URL = "http://localhost:9000/api/v3";
    private final Address ZERO_ADDRESS = new Address("cx0000000000000000000000000000000000000000");
    private final BigInteger NETWORK_ID = new BigInteger("3");

    private IconService iconService;
    private Wallet wallet;

    private SampleTokenTest() throws IOException {
        iconService = new IconService(new HttpProvider(URL));

        createAndStoreWallet();
//        File file = new File("/tmp/keystore.json");
//        try {
//            wallet = KeyWallet.load("test1234!", file);
//        } catch (IOException | CipherException e) {
//            e.printStackTrace();
//        }
    }

    private void createAndStoreWallet() throws IOException {
        try {
            wallet = KeyWallet.create();
            KeyWallet.store((KeyWallet) wallet, "P@sswOrd", new File("/tmp"));
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
            throw new IOException("Key creation failed!");
        } catch (CipherException e) {
            e.printStackTrace();
            throw new IOException("Key store failed!");
        }
    }

    private void deploy(String zipfile) throws IOException {
        String contentType = "application/zip";
        byte[] content = readFile(zipfile);
        long timestamp = System.currentTimeMillis() * 1000L;

        RpcObject params = new RpcObject.Builder()
                .put("initialSupply", new RpcValue(new BigInteger("1000")))
                .put("decimals", new RpcValue(new BigInteger("18")))
                .build();

        Transaction transaction = TransactionBuilder.of(NETWORK_ID)
                .from(wallet.getAddress())
                .to(ZERO_ADDRESS)
                .stepLimit(new BigInteger("20000000"))
                .timestamp(new BigInteger(Long.toString(timestamp)))
                .nonce(new BigInteger("1"))
                .deploy(contentType, content)
                .params(params)
                .build();

        SignedTransaction signedTransaction = new SignedTransaction(transaction, wallet);
        Bytes hash = iconService.sendTransaction(signedTransaction).execute();
        System.out.println("txHash: " + hash);
    }

    private byte[] readFile(String zipfile) throws IOException {
        Path path = Paths.get(zipfile);
        return Files.readAllBytes(path);
    }

    public static void main(String args[]) throws IOException {
        SampleTokenTest tokenTest = new SampleTokenTest();
        tokenTest.deploy("/tmp/sampleToken.zip");
    }
}
