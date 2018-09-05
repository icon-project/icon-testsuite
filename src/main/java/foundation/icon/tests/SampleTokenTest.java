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
import foundation.icon.icx.data.*;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcError;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import org.web3j.crypto.CipherException;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;

public class SampleTokenTest {
    private static final Address ZERO_ADDRESS = new Address("cx0000000000000000000000000000000000000000");
    static final BigInteger NETWORK_ID = new BigInteger("3");

    private static int txCount = 0;
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

    private Bytes checkGoalReached(Wallet fromWallet, Address scoreAddress) throws IOException {
        long timestamp = System.currentTimeMillis() * 1000L;
        Transaction transaction = TransactionBuilder.of(SampleTokenTest.NETWORK_ID)
                .from(fromWallet.getAddress())
                .to(scoreAddress)
                .stepLimit(new BigInteger("20000"))
                .timestamp(new BigInteger(Long.toString(timestamp)))
                .nonce(new BigInteger("1"))
                .call("check_goal_reached")
                .build();

        SignedTransaction signedTransaction = new SignedTransaction(transaction, fromWallet);
        return iconService.sendTransaction(signedTransaction).execute();
    }

    private BigInteger getIcxBalance(Address address) throws IOException {
        return iconService.getBalance(address).execute();
    }

    private TransactionResult getTransactionResult(Bytes txHash) throws IOException {
        TransactionResult result = null;
        while (result == null) {
            try {
                result = iconService.getTransactionResult(txHash).execute();
            } catch (RpcError e) {
                System.out.println("RpcError: code: " + e.getCode() + ", message: " + e.getMessage());
                try {
                    // wait until block confirmation
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

    private static void printTransactionHash(String header, Bytes txHash) {
        System.out.println(header + " txHash " + (++txCount) + ": " + txHash);
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
        String initialSupply = "1000";
        RpcObject params = new RpcObject.Builder()
                .put("initialSupply", new RpcValue(new BigInteger(initialSupply)))
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
        printTransactionHash("ICX transfer", txHash);

        // send 100 icx to Bob
        txHash = tokenTest.transferIcx(genesisWallet, bobWallet.getAddress(), "100");
        printTransactionHash("ICX transfer", txHash);

        // transfer all tokens to crowd sale score
        txHash = tokenScore.transfer(ownerWallet, crowdSaleScoreAddress, initialSupply);
        printTransactionHash("TOKEN transfer", txHash);

        // check if Alice has 50 icx
        while (true) {
            BigInteger icxBalance = tokenTest.getIcxBalance(aliceWallet.getAddress());
            System.out.println("ICX balance of Alice: " + icxBalance);
            if (icxBalance.equals(BigInteger.valueOf(0))) {
                try {
                    // wait until block confirmation
                    System.out.println("Sleep 1 second.");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (icxBalance.equals(BigInteger.valueOf(50).multiply(BigDecimal.TEN.pow(18).toBigInteger()))) {
                break;
            } else {
                throw new IOException("Balance mismatch!");
            }
        }

        // Alice: send 40 icx to crowd sale
        txHash = tokenTest.transferIcx(aliceWallet, crowdSaleScoreAddress, "40");
        printTransactionHash("ICX transfer", txHash);

        // Bob: send 60 icx to crowd sale
        txHash = tokenTest.transferIcx(bobWallet, crowdSaleScoreAddress, "60");
        printTransactionHash("ICX transfer", txHash);

        // check if Alice has 40 tokens for reward
        while (true) {
            balance = tokenScore.balanceOf(aliceWallet.getAddress());
            System.out.println("token balance of Alice: " + balance.asInteger());
            if (balance.asInteger().equals(BigInteger.valueOf(0))) {
                try {
                    // wait until block confirmation
                    System.out.println("Sleep 1 second.");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                break;
            }
        }
        // check if Bob has 60 tokens for reward
        balance = tokenScore.balanceOf(bobWallet.getAddress());
        System.out.println("token balance of Bob: " + balance.asInteger());

        // check if goal reached
        boolean exitLoop = false;
        while (true) {
            txHash = tokenTest.checkGoalReached(ownerWallet, crowdSaleScoreAddress);
            printTransactionHash("checkGoalReached", txHash);
            result = tokenTest.getTransactionResult(txHash);
            List<EventLog> eventLogs = result.getEventLogs();
            for (EventLog event : eventLogs) {
                if (event.getScoreAddress().equals(crowdSaleScoreAddress.toString())) {
                    String funcSig = event.getIndexed().get(0).asString();
                    System.out.println("function sig: " + funcSig);
                    exitLoop = true;
                }
            }
            if (exitLoop) {
                break;
            } else {
                try {
                    System.out.println("Sleep 1 second.");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // do safe withdrawal
    }
}
