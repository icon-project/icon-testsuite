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
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.data.TransactionResult.EventLog;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;

import java.io.IOException;
import java.math.BigInteger;

public class MultiSigWalletTest {
    private static final String WalletZipfile = "/ws/tests/multiSigWallet.zip";
    private static final String HelloZipfile = "/ws/tests/helloScore.zip";
    private static final BigInteger STATUS_SUCCESS = BigInteger.ONE;
    private static int txCount = 0;

    private MultiSigWalletTest() {
    }

    private static void printTransactionHash(String header, Bytes txHash) {
        System.out.println(header + ", txHash " + (++txCount) + ": " + txHash);
    }

    private static BigInteger getTransactionId(TransactionResult result, Address scoreAddress) throws IOException {
        EventLog event = Utils.findEventLogWithFuncSig(result, scoreAddress, "Submission(int)");
        if (event != null) {
            return event.getIndexed().get(1).asInteger();
        }
        throw new IOException("Failed to get transactionId.");
    }

    private static void ensureConfirmation(TransactionResult result, Address scoreAddress,
                                           Address sender, BigInteger txId) throws IOException {
        EventLog event = Utils.findEventLogWithFuncSig(result, scoreAddress, "Confirmation(Address,int)");
        if (event != null) {
            Address _sender = event.getIndexed().get(1).asAddress();
            BigInteger _txId = event.getIndexed().get(2).asInteger();
            if (sender.equals(_sender) && txId.equals(_txId)) {
                return; // ensured
            }
        }
        throw new IOException("Failed to get Confirmation.");
    }

    private static void ensureExecution(TransactionResult result, Address scoreAddress,
                                        BigInteger txId) throws IOException {
        EventLog event = Utils.findEventLogWithFuncSig(result, scoreAddress, "Execution(int)");
        if (event != null) {
            BigInteger _txId = event.getIndexed().get(1).asInteger();
            if (txId.equals(_txId)) {
                return; // ensured
            }
        }
        throw new IOException("Failed to get Execution.");
    }

    private static void ensureWalletOwnerAddition(TransactionResult result, Address scoreAddress,
                                                  Address address) throws IOException {
        EventLog event = Utils.findEventLogWithFuncSig(result, scoreAddress, "WalletOwnerAddition(Address)");
        if (event != null) {
            Address _address = event.getIndexed().get(1).asAddress();
            if (address.equals(_address)) {
                return; // ensured
            }
        }
        throw new IOException("Failed to get WalletOwnerAddition.");
    }

    private static void ensureWalletOwnerRemoval(TransactionResult result, Address scoreAddress,
                                                  Address address) throws IOException {
        EventLog event = Utils.findEventLogWithFuncSig(result, scoreAddress, "WalletOwnerRemoval(Address)");
        if (event != null) {
            Address _address = event.getIndexed().get(1).asAddress();
            if (address.equals(_address)) {
                return; // ensured
            }
        }
        throw new IOException("Failed to get WalletOwnerRemoval.");
    }

    private static void ensureRequirementChange(TransactionResult result, Address scoreAddress,
                                                Integer required) throws IOException {
        EventLog event = Utils.findEventLogWithFuncSig(result, scoreAddress, "RequirementChange(int)");
        if (event != null) {
            BigInteger _required = event.getData().get(0).asInteger();
            if (required.equals(_required.intValue())) {
                return; // ensured
            }
        }
        throw new IOException("Failed to get RequirementChange.");
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

        // *** Send 2 icx to Bob (EOA)
        // 1. tx is initiated by ownerWallet first
        txHash = multiSigWalletScore.submitIcxTransaction(ownerWallet, bobWallet.getAddress(), 2, "send 2 icx to Bob");
        printTransactionHash("#1 submitTransaction", txHash);
        result = Utils.getTransactionResult(iconService, txHash);
        BigInteger txId = getTransactionId(result, multiSigWalletAddress);

        // 2. Alice confirms the tx to make the tx executed
        txHash = multiSigWalletScore.confirmTransaction(aliceWallet, txId);
        printTransactionHash("#2 confirmTransaction", txHash);
        result = Utils.getTransactionResult(iconService, txHash);

        ensureConfirmation(result, multiSigWalletAddress, aliceWallet.getAddress(), txId);
        ensureExecution(result, multiSigWalletAddress, txId);

        // check icx balances
        Utils.ensureIcxBalance(iconService, multiSigWalletAddress, 3, 1);
        Utils.ensureIcxBalance(iconService, bobWallet.getAddress(), 0, 2);

        // *** Send 1 icx to Contract
        // deploy sample score to accept icx
        params = new RpcObject.Builder().build();
        txHash = Utils.deployScore(iconService, ownerWallet, HelloZipfile, params);
        printTransactionHash("HelloScore deploy", txHash);

        // get the address of hello score
        result = Utils.getTransactionResult(iconService, txHash);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to deploy hello score.");
        }
        Address helloScoreAddress = new Address(result.getScoreAddress());
        System.out.println("HelloScore address: " + helloScoreAddress);

        // 3. tx is initiated by ownerWallet first
        txHash = multiSigWalletScore.submitIcxTransaction(ownerWallet, helloScoreAddress, 1, "send 1 icx to hello");
        printTransactionHash("#3 submitTransaction", txHash);
        result = Utils.getTransactionResult(iconService, txHash);
        txId = getTransactionId(result, multiSigWalletAddress);

        // 4. Bob confirms the tx to make the tx executed
        txHash = multiSigWalletScore.confirmTransaction(bobWallet, txId);
        printTransactionHash("#4 confirmTransaction", txHash);
        result = Utils.getTransactionResult(iconService, txHash);

        ensureConfirmation(result, multiSigWalletAddress, bobWallet.getAddress(), txId);
        ensureExecution(result, multiSigWalletAddress, txId);

        // check icx balances
        Utils.ensureIcxBalance(iconService, multiSigWalletAddress, 1, 0);
        Utils.ensureIcxBalance(iconService, helloScoreAddress, 0, 1);

        // *** Add new wallet owner (charlie)
        KeyWallet charlieWallet = Utils.createAndStoreWallet();
        // 5. tx is initiated by ownerWallet first
        txHash = multiSigWalletScore.addWalletOwner(ownerWallet, charlieWallet.getAddress(), "add new wallet owner");
        printTransactionHash("#5 addWalletOwner", txHash);
        result = Utils.getTransactionResult(iconService, txHash);
        txId = getTransactionId(result, multiSigWalletAddress);

        // 6. Alice confirms the tx to make the tx executed
        txHash = multiSigWalletScore.confirmTransaction(aliceWallet, txId);
        printTransactionHash("#6 confirmTransaction", txHash);
        result = Utils.getTransactionResult(iconService, txHash);

        ensureConfirmation(result, multiSigWalletAddress, aliceWallet.getAddress(), txId);
        ensureWalletOwnerAddition(result, multiSigWalletAddress, charlieWallet.getAddress());
        ensureExecution(result, multiSigWalletAddress, txId);

        // *** Replace wallet owner (charlie -> david)
        KeyWallet davidWallet = Utils.createAndStoreWallet();
        // 7. tx is initiated by ownerWallet first
        txHash = multiSigWalletScore.replaceWalletOwner(ownerWallet, charlieWallet.getAddress(),
                                                        davidWallet.getAddress(), "replace wallet owner");
        printTransactionHash("#7 replaceWalletOwner", txHash);
        result = Utils.getTransactionResult(iconService, txHash);
        txId = getTransactionId(result, multiSigWalletAddress);

        // 8. Alice confirms the tx to make the tx executed
        txHash = multiSigWalletScore.confirmTransaction(aliceWallet, txId);
        printTransactionHash("#8 confirmTransaction", txHash);
        result = Utils.getTransactionResult(iconService, txHash);

        ensureConfirmation(result, multiSigWalletAddress, aliceWallet.getAddress(), txId);
        ensureWalletOwnerRemoval(result, multiSigWalletAddress, charlieWallet.getAddress());
        ensureWalletOwnerAddition(result, multiSigWalletAddress, davidWallet.getAddress());
        ensureExecution(result, multiSigWalletAddress, txId);

        // *** Change requirement
        // 9. tx is initiated by ownerWallet first
        txHash = multiSigWalletScore.changeRequirement(ownerWallet, 3, "change requirement to 3");
        printTransactionHash("#9 changeRequirement", txHash);
        result = Utils.getTransactionResult(iconService, txHash);
        txId = getTransactionId(result, multiSigWalletAddress);

        // 10. Alice confirms the tx to make the tx executed
        txHash = multiSigWalletScore.confirmTransaction(aliceWallet, txId);
        printTransactionHash("#10 confirmTransaction", txHash);
        result = Utils.getTransactionResult(iconService, txHash);

        ensureConfirmation(result, multiSigWalletAddress, aliceWallet.getAddress(), txId);
        ensureRequirementChange(result, multiSigWalletAddress, 3);
        ensureExecution(result, multiSigWalletAddress, txId);
    }
}
