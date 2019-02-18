/*
 * Copyright (c) 2019 ICON Foundation
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

import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.IconAmount;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.data.TransactionResult.EventLog;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

public class MultiSigWalletTest {
    private static final String WalletZipfile = "/ws/tests/multiSigWallet.zip";
    private static final BigInteger STATUS_SUCCESS = BigInteger.ONE;
    private static int txCount = 0;

    private MultiSigWalletTest() {
    }

    private static void printTransactionHash(String header, Bytes txHash) {
        System.out.println(header + ", txHash " + (++txCount) + ": " + txHash);
    }

    private static BigInteger getTransactionId(TransactionResult result, Address scoreAddress) {
        List<EventLog> eventLogs = result.getEventLogs();
        for (EventLog event : eventLogs) {
            if (event.getScoreAddress().equals(scoreAddress.toString())) {
                String funcSig = event.getIndexed().get(0).asString();
                System.out.println("function sig: " + funcSig);
                if ("Submission(int)".equals(funcSig)) {
                    return event.getIndexed().get(1).asInteger();
                }
            }
        }
        return null;
    }

    private static void ensureConfirmation(TransactionResult result, Address scoreAddress,
                                           Address sender, BigInteger txId) throws IOException {
        List<EventLog> eventLogs = result.getEventLogs();
        for (EventLog event : eventLogs) {
            if (event.getScoreAddress().equals(scoreAddress.toString())) {
                String funcSig = event.getIndexed().get(0).asString();
                System.out.println("function sig: " + funcSig);
                if ("Confirmation(Address,int)".equals(funcSig)) {
                    Address _sender = event.getIndexed().get(1).asAddress();
                    BigInteger _txId = event.getIndexed().get(2).asInteger();
                    if (!sender.equals(_sender) || !txId.equals(_txId)) {
                        throw new IOException("Failed to get Confirmation.");
                    }
                    return;
                }
            }
        }
    }

    private static void ensureExecution(TransactionResult result, Address scoreAddress,
                                        BigInteger txId) throws IOException {
        List<EventLog> eventLogs = result.getEventLogs();
        for (EventLog event : eventLogs) {
            if (event.getScoreAddress().equals(scoreAddress.toString())) {
                String funcSig = event.getIndexed().get(0).asString();
                System.out.println("function sig: " + funcSig);
                if ("Execution(int)".equals(funcSig)) {
                    BigInteger _txId = event.getIndexed().get(1).asInteger();
                    if (!txId.equals(_txId)) {
                        throw new IOException("Failed to get Execution.");
                    }
                    return;
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        IconService iconService = new IconService(new HttpProvider(Constants.ENDPOINT_URL_LOCAL));

        KeyWallet godWallet = Utils.readWalletFromFile("/ws/tests/keystore_test1.json", "test1_Account");
        KeyWallet ownerWallet = Utils.createAndStoreWallet();
        KeyWallet aliceWallet = Utils.createAndStoreWallet();
        KeyWallet bobWallet = Utils.createAndStoreWallet();
        System.out.println("Address of owner: " + ownerWallet.getAddress());
        System.out.println("Address of Alice: " + aliceWallet.getAddress());
        System.out.println("Address of Bob:   " + bobWallet.getAddress());

        // deploy MultiSigWallet score
        String walletOwners = ownerWallet.getAddress() + "," +
                              aliceWallet.getAddress() + "," +
                              bobWallet.getAddress();
        RpcObject params = new RpcObject.Builder()
                .put("_walletOwners", new RpcValue(walletOwners))
                .put("_required", new RpcValue(new BigInteger("2")))
                .build();
        Bytes txHash = Utils.deployScore(iconService, ownerWallet, WalletZipfile, params);
        printTransactionHash("MultiSigWallet SCORE deploy", txHash);

        // get the address of MultiSigWallet score
        TransactionResult result = Utils.getTransactionResult(iconService, txHash);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to deploy MultiSigWallet score.");
        }
        Address multiSigWalletAddress = new Address(result.getScoreAddress());
        System.out.println("MultiSigWallet SCORE address: " + multiSigWalletAddress);

        // create a MultiSigWallet score instance
        MultiSigWalletScore multiSigWalletScore = new MultiSigWalletScore(iconService, multiSigWalletAddress);

        // send 3 icx to the multiSigWallet
        txHash = Utils.transferIcx(iconService, godWallet, multiSigWalletAddress, "3");
        printTransactionHash("ICX transfer to multiSigWallet", txHash);
        Utils.ensureIcxBalance(iconService, multiSigWalletAddress, 0, 3);

        // send 2 icx to Bob
        // 1. tx is initiated by ownerWallet first
        BigInteger two_icx = IconAmount.of("2", IconAmount.Unit.ICX).toLoop();
        params = new RpcObject.Builder()
                .put("_destination", new RpcValue(bobWallet.getAddress()))
                .put("_value", new RpcValue(two_icx))
                .put("_description", new RpcValue("send 2 icx to Bob"))
                .build();
        txHash = multiSigWalletScore.submitTransaction(ownerWallet, params);
        printTransactionHash("#1 submitTransaction", txHash);

        result = Utils.getTransactionResult(iconService, txHash);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to execute submitTransaction.");
        }
        BigInteger txId = getTransactionId(result, multiSigWalletAddress);
        if (txId == null) {
            throw new IOException("Failed to get transactionId.");
        }

        // 2. Alice confirms the tx to make the tx executed
        txHash = multiSigWalletScore.confirmTransaction(aliceWallet, txId);
        printTransactionHash("#2 confirmTransaction", txHash);

        result = Utils.getTransactionResult(iconService, txHash);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to execute confirmTransaction.");
        }
        ensureConfirmation(result, multiSigWalletAddress, aliceWallet.getAddress(), txId);
        ensureExecution(result, multiSigWalletAddress, txId);

        // check icx balances
        Utils.ensureIcxBalance(iconService, multiSigWalletAddress, 3, 1);
        Utils.ensureIcxBalance(iconService, bobWallet.getAddress(), 0, 2);
    }
}
