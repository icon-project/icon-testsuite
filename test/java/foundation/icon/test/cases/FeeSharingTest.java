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

package foundation.icon.test.cases;

import foundation.icon.icx.Call;
import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.SignedTransaction;
import foundation.icon.icx.Transaction;
import foundation.icon.icx.TransactionBuilder;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.IconAmount;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcArray;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.Constants;
import foundation.icon.test.score.FeeShareScore;
import foundation.icon.test.Utils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;

public class FeeSharingTest {
    private static final String FeeShareZip = "/ws/tests/feeShare.zip";
    private static final BigInteger STATUS_SUCCESS = BigInteger.ONE;
    private static int txCount = 0;

    private final IconService iconService;
    private final KeyWallet ownerWallet;
    private final KeyWallet aliceWallet;
    private final Address scoreAddress;
    private BigInteger aliceBalance;

    private static void printTransactionHash(String header, Bytes txHash) {
        System.out.println(header + ", txHash " + (++txCount) + ": " + txHash);
    }

    private static BigInteger ensureIcxBalanceDecreased(IconService iconService, Address address, BigInteger val) throws IOException {
        BigInteger balance = iconService.getBalance(address).execute();
        System.out.println("ICX balance of " + address + ": " + balance);
        if (balance.compareTo(val) >= 0) {
            throw new IOException("Balance not decreased!");
        }
        return balance;
    }

    private FeeSharingTest() throws IOException {
        iconService = new IconService(new HttpProvider(Constants.SERVER_URI, 3));

        KeyWallet godWallet = Utils.readWalletFromFile("/ws/tests/keystore_test1.json", "test1_Account");
        ownerWallet = Utils.createAndStoreWallet();
        aliceWallet = Utils.createAndStoreWallet();
        System.out.println("Address of owner: " + ownerWallet.getAddress());
        System.out.println("Address of alice: " + aliceWallet.getAddress());

        // transfer initial icx to test addresses
        String ownerBalance = "5100"; // deploy + deposit
        Bytes txHash = Utils.transferIcx(iconService, godWallet, ownerWallet.getAddress(), ownerBalance);
        printTransactionHash("ICX transfer", txHash);
        Utils.ensureIcxBalance(iconService, ownerWallet.getAddress(), 0, Long.valueOf(ownerBalance));
        txHash = Utils.transferIcx(iconService, godWallet, aliceWallet.getAddress(), "1");
        printTransactionHash("ICX transfer", txHash);
        aliceBalance = Utils.ensureIcxBalance(iconService, aliceWallet.getAddress(), 0, 1);

        scoreAddress = deployScore(FeeShareZip, null);
    }

    private Address deployScore(String zipfile, RpcObject params) throws IOException {
        Bytes txHash = Utils.deployScore(iconService, ownerWallet, zipfile, params);
        printTransactionHash("SCORE deploy", txHash);

        // get the address of the SCORE
        TransactionResult result = Utils.getTransactionResult(iconService, txHash);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to deploy");
        }
        Address scoreAddress = new Address(result.getScoreAddress());
        System.out.println("SCORE address: " + scoreAddress);
        return scoreAddress;
    }

    private Bytes addDeposit(BigInteger depositAmount) throws IOException {
        System.out.println("addDeposit: " + depositAmount);
        Transaction transaction = TransactionBuilder.newBuilder()
                .nid(BigInteger.valueOf(3))
                .from(ownerWallet.getAddress())
                .to(scoreAddress)
                .value(depositAmount)
                .stepLimit(new BigInteger("200000"))
                .deposit()
                .add()
                .build();
        SignedTransaction signedTransaction = new SignedTransaction(transaction, ownerWallet);
        Bytes txHash = iconService.sendTransaction(signedTransaction).execute();
        System.out.println("txHash: " + txHash);
        TransactionResult result = Utils.getTransactionResult(iconService, txHash);
        if (!BigInteger.ONE.equals(result.getStatus())) {
            throw new IOException("Add deposit failed!");
        }
        return txHash;
    }

    private Bytes withdrawDeposit(Bytes depositId) throws IOException {
        System.out.println("withdrawDeposit: " + depositId);
        Transaction transaction = TransactionBuilder.newBuilder()
                .nid(BigInteger.valueOf(3))
                .from(ownerWallet.getAddress())
                .to(scoreAddress)
                .stepLimit(new BigInteger("200000"))
                .deposit()
                .withdraw(depositId)
                .build();
        SignedTransaction signedTransaction = new SignedTransaction(transaction, ownerWallet);
        Bytes txHash = iconService.sendTransaction(signedTransaction).execute();
        System.out.println("txHash: " + txHash);
        TransactionResult result = Utils.getTransactionResult(iconService, txHash);
        if (!BigInteger.ONE.equals(result.getStatus())) {
            throw new IOException("Withdraw deposit failed!");
        }
        return txHash;
    }

    private void runTest() throws IOException {
        FeeShareScore feeShareOwner = new FeeShareScore(iconService, ownerWallet, scoreAddress);
        System.out.println("value: " + feeShareOwner.getValue());

        // add alice into the white list
        TransactionResult result = feeShareOwner.addToWhitelist(aliceWallet.getAddress(), 100);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to invoke addToWhitelist");
        }

        // set value before adding deposit (user balance should be decreased)
        FeeShareScore feeShareAlice = new FeeShareScore(iconService, aliceWallet, scoreAddress);
        result = feeShareAlice.setValue("alice #1");
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to invoke setValue");
        }
        System.out.println("value: " + feeShareAlice.getValue());
        // check if the balance was decreased
        aliceBalance = ensureIcxBalanceDecreased(iconService, aliceWallet.getAddress(), aliceBalance);

        // add deposit to SCORE
        BigInteger depositAmount = IconAmount.of("5000", IconAmount.Unit.ICX).toLoop();
        Bytes depositId = addDeposit(depositAmount);
        printDepositInfo(scoreAddress);

        // set value after adding deposit (user balance should NOT be decreased)
        result = feeShareAlice.setValue("alice #2");
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to invoke setValue");
        }
        System.out.println("txHash: " + result.getTxHash());
        printStepUsedDetails(result.getStepUsedDetails());
        System.out.println("value: " + feeShareAlice.getValue());
        System.out.println("stepUsed: " + result.getStepUsed());
        // check if the balance was NOT changed
        aliceBalance = Utils.ensureIcxBalance(iconService, aliceWallet.getAddress(), aliceBalance);
        printDepositInfo(scoreAddress);

        // withdraw the deposit
        withdrawDeposit(depositId);

        // set value after withdrawing deposit (user balance should be decreased again)
        result = feeShareAlice.setValue("alice #3");
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to invoke setValue");
        }
        System.out.println("value: " + feeShareAlice.getValue());
        aliceBalance = ensureIcxBalanceDecreased(iconService, aliceWallet.getAddress(), aliceBalance);
    }

    private void printStepUsedDetails(RpcItem stepUsedDetails) {
        RpcObject details = stepUsedDetails.asObject();
        System.out.println("stepUsedDetails: {");
        String M1 = "    ";
        for (String key : details.keySet()) {
            System.out.printf(M1 + "%s: %s\n", key, details.getItem(key).asInteger());
        }
        System.out.println("}");
    }

    private void printDepositInfo(Address scoreAddress) throws IOException {
        RpcItem status = GovScore.getScoreStatus(iconService, scoreAddress);
        RpcItem item = status.asObject().getItem("depositInfo");
        if (item != null) {
            System.out.println("depositInfo: {");
            RpcObject info = item.asObject();
            for (String key : info.keySet()) {
                String M1 = "    ";
                if (key.equals("deposits")) {
                    RpcArray deposits = info.getItem("deposits").asArray();
                    System.out.println(M1 + "deposits: {");
                    String M2 = M1 + M1;
                    RpcObject deposit = deposits.get(0).asObject();
                    for (String key2 : deposit.keySet()) {
                        if (key2.equals("id") || key2.equals("sender")) {
                            System.out.printf(M2 + "%s: %s\n", key2, deposit.getItem(key2).asValue());
                        } else {
                            System.out.printf(M2 + "%s: %s\n", key2, deposit.getItem(key2).asInteger());
                        }
                    }
                    System.out.println(M1 + "}");
                } else if (key.equals("scoreAddress")){
                    System.out.printf(M1 + "%s: %s\n", key, info.getItem(key).asAddress());
                } else {
                    System.out.printf(M1 + "%s: %s\n", key, info.getItem(key).asInteger());
                }
            }
            System.out.println("}");
        } else {
            System.out.println("depositInfo NULL");
        }
    }

    private static class GovScore {
        private final static Address govAddress = new Address("cx0000000000000000000000000000000000000001");

        static RpcItem getScoreStatus(IconService iconService, Address scoreAddress) throws IOException {
            RpcObject params = new RpcObject.Builder()
                    .put("address", new RpcValue(scoreAddress))
                    .build();
            Call<RpcItem> call = new Call.Builder()
                    .to(govAddress)
                    .method("getScoreStatus")
                    .params(params)
                    .build();
            return iconService.call(call).execute();
        }
    }

    @Test
    public void testAll() throws IOException {
        FeeSharingTest test = new FeeSharingTest();
        test.runTest();
    }
}
