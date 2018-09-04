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
import foundation.icon.icx.data.IconAmount;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcError;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
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
    private final Address ZERO_ADDRESS = new Address("cx0000000000000000000000000000000000000000");
    private final BigInteger NETWORK_ID = new BigInteger("3");

    private IconService iconService;

    private SampleTokenTest(IconService iconService) {
        this.iconService = iconService;
    }

    private Bytes transferIcx(Wallet fromWallet, Address to, String value) throws IOException {
        long timestamp = System.currentTimeMillis() * 1000L;
        Transaction transaction = TransactionBuilder.of(NETWORK_ID)
                .from(fromWallet.getAddress())
                .to(to)
                .value(IconAmount.of(value, IconAmount.Unit.ICX).toLoop())
                .stepLimit(new BigInteger("10000"))
                .timestamp(new BigInteger(Long.toString(timestamp)))
                .nonce(new BigInteger("1"))
                .build();

        SignedTransaction signedTransaction = new SignedTransaction(transaction, fromWallet);
        return iconService.sendTransaction(signedTransaction).execute();
    }

    private Bytes deployScore(Wallet fromWallet, String zipfile, RpcObject params) throws IOException {
        String contentType = "application/zip";
        byte[] content = readFile(zipfile);
        long timestamp = System.currentTimeMillis() * 1000L;
        Transaction transaction = TransactionBuilder.of(NETWORK_ID)
                .from(fromWallet.getAddress())
                .to(ZERO_ADDRESS)
                .stepLimit(new BigInteger("20000000"))
                .timestamp(new BigInteger(Long.toString(timestamp)))
                .nonce(new BigInteger("1"))
                .deploy(contentType, content)
                .params(params)
                .build();

        SignedTransaction signedTransaction = new SignedTransaction(transaction, fromWallet);
        return iconService.sendTransaction(signedTransaction).execute();
    }

    private TransactionResult getTransactionResult(Bytes txHash) throws IOException {
        TransactionResult result = null;
        while (result == null) {
            try {
                result = iconService.getTransactionResult(txHash).execute();
            } catch (RpcError e) {
                System.out.println("RpcError: code: " + e.getCode() + ", message: " + e.getMessage());
                try {
                    System.out.println("Sleep 1 second.");
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
        return result;
    }

    private static byte[] readFile(String zipfile) throws IOException {
        Path path = Paths.get(zipfile);
        return Files.readAllBytes(path);
    }

    private static KeyWallet createAndStoreWallet() throws IOException {
        try {
            KeyWallet wallet = KeyWallet.create();
            KeyWallet.store(wallet, "P@sswOrd", new File("/tmp"));
            return wallet;
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            e.printStackTrace();
            throw new IOException("Key creation failed!");
        } catch (CipherException e) {
            e.printStackTrace();
            throw new IOException("Key store failed!");
        }
    }

    private static KeyWallet readWalletFromFile(String path) throws IOException {
        try {
            File file = new File(path);
            return KeyWallet.load("P@sswOrd", file);
        } catch (CipherException e) {
            e.printStackTrace();
            throw new IOException("Key load failed!");
        }
    }

    public static void main(String args[]) throws IOException {
        final String URL = "http://localhost:9000/api/v3";
        IconService iconService = new IconService(new HttpProvider(URL));
        SampleTokenTest tokenTest = new SampleTokenTest(iconService);

        KeyWallet genesisWallet = readWalletFromFile("/ws/tests/keystore_genesis.json");
        KeyWallet ownerWallet = createAndStoreWallet();
        KeyWallet aliceWallet = createAndStoreWallet();
        KeyWallet bobWallet = createAndStoreWallet();
        System.out.println("Address of owner: " + ownerWallet.getAddress());

        // deploy sample token
        RpcObject params = new RpcObject.Builder()
                .put("initialSupply", new RpcValue(new BigInteger("1000")))
                .put("decimals", new RpcValue(new BigInteger("18")))
                .build();
        Bytes txHash = tokenTest.deployScore(ownerWallet, "/ws/tests/sampleToken.zip", params);
        System.out.println("SampleToken deploy txHash: " + txHash);

        // get the address of token score
        TransactionResult result = tokenTest.getTransactionResult(txHash);
        Address tokenScoreAddress = new Address(result.getScoreAddress());
        System.out.println("SampleToken address: " + tokenScoreAddress);

        // deploy crowd sale
        params = new RpcObject.Builder()
                .put("fundingGoalInIcx", new RpcValue(new BigInteger("100")))
                .put("tokenScore", new RpcValue(tokenScoreAddress))
                .put("durationInBlocks", new RpcValue(new BigInteger("16")))
                .build();
        txHash = tokenTest.deployScore(ownerWallet, "/ws/tests/crowdSale.zip", params);
        System.out.println("CrowdSale deploy txHash: " + txHash);

        // get the address of crowd sale score
        result = tokenTest.getTransactionResult(txHash);
        Address crowdSaleScoreAddress = new Address(result.getScoreAddress());
        System.out.println("CrowdSaleScore address: " + crowdSaleScoreAddress);

        // check the initial token supply owned by score deployer
        TokenScore tokenScore = new TokenScore(iconService, tokenScoreAddress);
        RpcItem balance = tokenScore.balanceOf(ownerWallet.getAddress());
        System.out.println("initial token supply: " + balance.asInteger());

        // send 50 icx to Alice
        txHash = tokenTest.transferIcx(genesisWallet, aliceWallet.getAddress(), "50");
        System.out.println("transferIcx txHash 1: " + txHash);

        // send 100 icx to Bob
        txHash = tokenTest.transferIcx(genesisWallet, bobWallet.getAddress(), "100");
        System.out.println("transferIcx txHash 2: " + txHash);

        // transfer all tokens to crowd sale score

        // Alice: send 40 icx to crowd sale

        // Bob: send 60 icx to crowd sale

        // check goal reached

        // do safe withdrawal
    }
}
