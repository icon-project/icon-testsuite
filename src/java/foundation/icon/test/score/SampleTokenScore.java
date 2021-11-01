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

import com.iconloop.score.token.irc2.IRC2;
import com.iconloop.score.token.irc2.IRC2Basic;
import contract.IRC2BasicToken;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.Constants;
import foundation.icon.test.ResultTimeoutException;
import foundation.icon.test.TransactionFailureException;
import foundation.icon.test.TransactionHandler;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import static foundation.icon.test.Env.LOG;

public class SampleTokenScore extends Score {
    private static final Class<?>[] javaTokenClasses = new Class<?>[]{
            IRC2BasicToken.class, IRC2Basic.class, IRC2.class};

    public static SampleTokenScore mustDeploy(TransactionHandler txHandler, Wallet owner,
                                              BigInteger decimals, BigInteger initialSupply)
            throws ResultTimeoutException, TransactionFailureException, IOException {
        return mustDeploy(txHandler, owner, decimals, initialSupply, Constants.CONTENT_TYPE_PYTHON);
    }

    public static SampleTokenScore mustDeploy(TransactionHandler txHandler, Wallet owner,
                                              BigInteger decimals, BigInteger initialSupply, String contentType)
            throws ResultTimeoutException, TransactionFailureException, IOException {
        LOG.infoEntering("deploy", "SampleToken");
        Score score;
        if (contentType.equals(Constants.CONTENT_TYPE_PYTHON)) {
            score = txHandler.deploy(owner, getFilePath("sample_token"), getParams(decimals, initialSupply));
        } else if (contentType.equals(Constants.CONTENT_TYPE_JAVA)) {
            score = txHandler.deploy(owner, javaTokenClasses, getParams(decimals, initialSupply));
        } else {
            throw new IllegalArgumentException("Unknown content type");
        }
        LOG.info("scoreAddr = " + score.getAddress());
        LOG.infoExiting();
        return new SampleTokenScore(score);
    }

    private static RpcObject getParams(BigInteger decimals, BigInteger initialSupply) {
        return new RpcObject.Builder()
                .put("_name", new RpcValue("MySampleToken"))
                .put("_symbol", new RpcValue("MST"))
                .put("_decimals", new RpcValue(decimals))
                .put("_initialSupply", new RpcValue(initialSupply))
                .build();
    }

    public SampleTokenScore(Score other) {
        super(other);
    }

    public Bytes updateToJavaScore(Wallet owner) throws IOException {
        // the following params must not be overwritten when the Java score is updated
        return updateScore(owner, javaTokenClasses, new RpcObject.Builder()
                .put("_name", new RpcValue("MySampleToken Updated"))
                .put("_symbol", new RpcValue("MST2"))
                .put("_decimals", new RpcValue(BigInteger.ZERO))
                .put("_initialSupply", new RpcValue(BigInteger.ZERO))
                .build());
    }

    public BigInteger balanceOf(Address owner) throws IOException {
        RpcObject params = new RpcObject.Builder()
                .put("_owner", new RpcValue(owner))
                .build();
        return call("balanceOf", params).asInteger();
    }

    public TransactionResult transfer(Wallet wallet, Address to, BigInteger value)
            throws IOException, ResultTimeoutException {
        return this.transfer(wallet, to, value, null);
    }

    public TransactionResult transfer(Wallet wallet, Address to, BigInteger value, byte[] data)
            throws IOException, ResultTimeoutException {
        RpcObject.Builder builder = new RpcObject.Builder()
                .put("_to", new RpcValue(to))
                .put("_value", new RpcValue(value));
        if (data != null) {
            builder.put("_data", new RpcValue(data));
        }
        return this.invokeAndWaitResult(wallet, "transfer", builder.build());
    }

    public void ensureTransfer(TransactionResult result, Address from, Address to, BigInteger value, byte[] data)
            throws IOException {
        TransactionResult.EventLog event = findEventLog(result, getAddress(), "Transfer(Address,Address,int,bytes)");
        if (event != null) {
            if (data == null) {
                data = "None".getBytes();
            }
            Address _from = event.getIndexed().get(1).asAddress();
            Address _to = event.getIndexed().get(2).asAddress();
            BigInteger _value = event.getIndexed().get(3).asInteger();
            byte[] _data = event.getData().get(0).asByteArray();
            if (from.equals(_from) && to.equals(_to) && value.equals(_value) && Arrays.equals(data, _data)) {
                return; // ensured
            }
        }
        throw new IOException("ensureTransfer failed.");
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
}
