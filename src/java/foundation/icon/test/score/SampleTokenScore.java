/*
 * Copyright 2018 ICON Foundation
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

package foundation.icon.test.score;

import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.IconAmount;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.Constants;
import foundation.icon.test.ResultTimeoutException;
import foundation.icon.test.TransactionFailureException;
import foundation.icon.test.TransactionHandler;
import foundation.icon.test.Utils;

import java.io.IOException;
import java.math.BigInteger;

import static foundation.icon.test.Env.LOG;

public class SampleTokenScore extends Score {
    public static SampleTokenScore mustDeploy(TransactionHandler txHandler, Wallet owner,
                                              BigInteger decimals, BigInteger initialSupply)
            throws ResultTimeoutException, TransactionFailureException, IOException {
        LOG.infoEntering("deploy", "SampleToken");
        RpcObject params = new RpcObject.Builder()
                .put("_name", new RpcValue("MySampleToken"))
                .put("_symbol", new RpcValue("MST"))
                .put("_decimals", new RpcValue(decimals))
                .put("_initialSupply", new RpcValue(initialSupply))
                .build();
        Score score = txHandler.deploy(owner, getFilePath("sample_token"), params);
        LOG.info("scoreAddr = " + score.getAddress());
        LOG.infoExiting();
        return new SampleTokenScore(score);
    }

    public SampleTokenScore(Score other) {
        super(other);
    }

    public BigInteger balanceOf(Address owner) throws IOException {
        RpcObject params = new RpcObject.Builder()
                .put("_owner", new RpcValue(owner))
                .build();
        return call("balanceOf", params).asInteger();
    }

    public void ensureTokenBalance(Address owner, long value) throws ResultTimeoutException, IOException {
        long limitTime = System.currentTimeMillis() + Constants.DEFAULT_WAITING_TIME;
        while (true) {
            BigInteger balance = balanceOf(owner);
            String msg = "Token balance of " + owner + ": " + balance;
            if (balance.equals(BigInteger.valueOf(0))) {
                try {
                    if (limitTime < System.currentTimeMillis()) {
                        throw new ResultTimeoutException();
                    }
                    // wait until block confirmation
                    LOG.info(msg + "; Retry in 1 sec.");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (balance.equals(BigInteger.valueOf(value).multiply(BigInteger.TEN.pow(18)))) {
                LOG.info(msg);
                break;
            } else {
                throw new IOException("Token balance mismatch!");
            }
        }
    }

    public Bytes transfer(Wallet fromWallet, Address toAddress, BigInteger valueInIcx) throws IOException {
        RpcObject params = new RpcObject.Builder()
                .put("_to", new RpcValue(toAddress))
                .put("_value", new RpcValue(IconAmount.of(valueInIcx, 18).toLoop()))
                .build();
        return this.invoke(fromWallet, "transfer", params);
    }

    public void ensureFundTransfer(TransactionResult result, Address scoreAddress,
                                   Address backer, BigInteger amount) throws IOException {
        TransactionResult.EventLog event = Utils.findEventLogWithFuncSig(result, scoreAddress, "FundTransfer(Address,int,bool)");
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
}
