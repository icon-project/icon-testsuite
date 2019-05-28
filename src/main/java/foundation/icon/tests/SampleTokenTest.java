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

import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.IconAmount;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.data.TransactionResult.EventLog;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

public class SampleTokenTest {
    private static final String TokenZipfile = "/ws/tests/sampleToken.zip";
    private static final String CrowdSaleZipfile = "/ws/tests/crowdSale.zip";
    private static final BigInteger STATUS_SUCCESS = BigInteger.ONE;

    private static int txCount = 0;

    private SampleTokenTest() {
    }

    private static void printTransactionHash(String header, Bytes txHash) {
        System.out.println(header + ", txHash " + (++txCount) + ": " + txHash);
    }

    private static void ensureTokenBalance(TokenScore tokenScore, KeyWallet wallet, long value) throws IOException {
        while (true) {
            RpcItem result = tokenScore.balanceOf(wallet.getAddress());
            BigInteger balance = result.asInteger();
            System.out.println("Token balance of " + wallet.getAddress() + ": " + balance);
            if (balance.equals(BigInteger.valueOf(0))) {
                try {
                    // wait until block confirmation
                    System.out.println("Sleep 1 second.");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (balance.equals(BigInteger.valueOf(value).multiply(BigDecimal.TEN.pow(18).toBigInteger()))) {
                break;
            } else {
                throw new IOException("Token balance mismatch!");
            }
        }
    }

    private static void ensureFundTransfer(TransactionResult result, Address scoreAddress,
                                           Address backer, BigInteger amount) throws IOException {
        EventLog event = Utils.findEventLogWithFuncSig(result, scoreAddress, "FundTransfer(Address,int,bool)");
        if (event != null) {
            Address _backer = event.getIndexed().get(1).asAddress();
            BigInteger _amount = event.getIndexed().get(2).asInteger();
            Boolean isContribution = event.getIndexed().get(3).asBoolean();
            if (backer.equals(_backer) && amount.equals(_amount) && !isContribution) {
                return; // ensured
            }
        }
        throw new IOException("ensureFundTransfer failed.");
    }

    public static void main(String[] args) throws IOException {
        IconService iconService = new IconService(new HttpProvider(Constants.ENDPOINT_URL_LOCAL));

        KeyWallet godWallet = Utils.readWalletFromFile("/ws/tests/keystore_test1.json", "test1_Account");
        KeyWallet ownerWallet = Utils.createAndStoreWallet();
        KeyWallet aliceWallet = Utils.createAndStoreWallet();
        KeyWallet bobWallet = Utils.createAndStoreWallet();
        System.out.println("Address of owner: " + ownerWallet.getAddress());

        // transfer initial icx to owner address
        Bytes txHash = Utils.transferIcx(iconService, godWallet, ownerWallet.getAddress(), "100");
        printTransactionHash("ICX transfer", txHash);
        Utils.ensureIcxBalance(iconService, ownerWallet.getAddress(), 0, 100);

        // deploy sample token
        String initialSupply = "1000";
        RpcObject params = new RpcObject.Builder()
                .put("_initialSupply", new RpcValue(new BigInteger(initialSupply)))
                .put("_decimals", new RpcValue(new BigInteger("18")))
                .build();
        txHash = Utils.deployScore(iconService, ownerWallet, TokenZipfile, params);
        printTransactionHash("SampleToken deploy", txHash);

        // get the address of token score
        TransactionResult result = Utils.getTransactionResult(iconService, txHash);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to deploy.");
        }
        Address tokenScoreAddress = new Address(result.getScoreAddress());
        System.out.println("SampleToken address: " + tokenScoreAddress);

        // check the initial token supply owned by score deployer
        TokenScore tokenScore = new TokenScore(iconService, tokenScoreAddress);
        RpcItem balance = tokenScore.balanceOf(ownerWallet.getAddress());
        System.out.println("initial token supply: " + balance.asInteger());

        // deploy crowd sale
        params = new RpcObject.Builder()
                .put("_fundingGoalInIcx", new RpcValue(new BigInteger("100")))
                .put("_tokenScore", new RpcValue(tokenScoreAddress))
                .put("_durationInBlocks", new RpcValue(new BigInteger("10")))
                .build();
        txHash = Utils.deployScore(iconService, ownerWallet, CrowdSaleZipfile, params);
        printTransactionHash("CrowdSale deploy", txHash);

        // get the address of crowd sale score
        result = Utils.getTransactionResult(iconService, txHash);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to deploy.");
        }
        Address crowdSaleScoreAddress = new Address(result.getScoreAddress());
        System.out.println("CrowdSaleScore address: " + crowdSaleScoreAddress);

        // create a CrowdSale instance
        CrowdSaleScore crowdSaleScore = new CrowdSaleScore(iconService, crowdSaleScoreAddress);

        // send 50 icx to Alice
        txHash = Utils.transferIcx(iconService, godWallet, aliceWallet.getAddress(), "50");
        printTransactionHash("ICX transfer", txHash);

        // send 100 icx to Bob
        txHash = Utils.transferIcx(iconService, godWallet, bobWallet.getAddress(), "100");
        printTransactionHash("ICX transfer", txHash);

        // check icx balances of Alice and Bob
        Utils.ensureIcxBalance(iconService, aliceWallet.getAddress(), 0, 50);
        Utils.ensureIcxBalance(iconService, bobWallet.getAddress(), 0, 100);

        // transfer all tokens to crowd sale score
        txHash = tokenScore.transfer(ownerWallet, crowdSaleScoreAddress, initialSupply);
        printTransactionHash("TOKEN transfer", txHash);

        // Alice: send 40 icx to crowd sale
        txHash = Utils.transferIcx(iconService, aliceWallet, crowdSaleScoreAddress, "40");
        printTransactionHash("ICX transfer", txHash);

        // Bob: send 60 icx to crowd sale
        txHash = Utils.transferIcx(iconService, bobWallet, crowdSaleScoreAddress, "60");
        printTransactionHash("ICX transfer", txHash);

        // check token balances of Alice and Bob for reward
        ensureTokenBalance(tokenScore, aliceWallet, 40);
        ensureTokenBalance(tokenScore, bobWallet, 60);

        // check if goal reached
        while (true) {
            txHash = crowdSaleScore.checkGoalReached(ownerWallet);
            printTransactionHash("checkGoalReached", txHash);
            result = Utils.getTransactionResult(iconService, txHash);
            if (!STATUS_SUCCESS.equals(result.getStatus())) {
                throw new IOException("Failed to execute checkGoalReached.");
            }
            EventLog event = Utils.findEventLogWithFuncSig(result, crowdSaleScoreAddress, "GoalReached(Address,int)");
            if (event != null) {
                break;
            }
            try {
                System.out.println("Sleep 1 second.");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // do safe withdrawal
        txHash = crowdSaleScore.safeWithdrawal(ownerWallet);
        printTransactionHash("safeWithdrawal", txHash);
        result = Utils.getTransactionResult(iconService, txHash);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to execute safeWithdrawal.");
        }
        BigInteger amount = IconAmount.of("100", IconAmount.Unit.ICX).toLoop();
        ensureFundTransfer(result, crowdSaleScoreAddress, ownerWallet.getAddress(), amount);

        // check the final icx balance of owner
        //Utils.ensureIcxBalance(iconService, ownerWallet.getAddress(), 100, 200);
    }
}
