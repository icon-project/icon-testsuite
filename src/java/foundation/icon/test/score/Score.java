/*
 * Copyright 2019 ICON Foundation
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

import foundation.icon.icx.Call;
import foundation.icon.icx.Transaction;
import foundation.icon.icx.TransactionBuilder;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.test.Constants;
import foundation.icon.test.ResultTimeoutException;
import foundation.icon.test.TransactionHandler;

import java.io.IOException;
import java.math.BigInteger;

public class Score {
    private static final String SCORE_ROOT = "./test/scores/";
    private final TransactionHandler txHandler;
    private final Address address;

    public Score(TransactionHandler txHandler, Address scoreAddress) {
        this.txHandler = txHandler;
        this.address = scoreAddress;
    }

    public Score(Score other) {
        this(other.txHandler, other.address);
    }

    protected static String getFilePath(String pkgName) {
        return SCORE_ROOT + pkgName;
    }

    public RpcItem call(String method, RpcObject params)
            throws IOException {
        if (params == null) {
            params = new RpcObject.Builder().build();
        }
        Call<RpcItem> call = new Call.Builder()
                .to(getAddress())
                .method(method)
                .params(params)
                .build();
        return this.txHandler.call(call);
    }

    public Bytes invoke(Wallet wallet, String method, RpcObject params) throws IOException {
        return invoke(wallet, method, params, BigInteger.ZERO, Constants.DEFAULT_STEPS);
    }

    public Bytes invoke(Wallet wallet, String method, RpcObject params,
                        long value, long steps) throws IOException {
        return invoke(wallet, method, params, BigInteger.valueOf(value), BigInteger.valueOf(steps));
    }

    public Bytes invoke(Wallet wallet, String method, RpcObject params,
                        BigInteger value, BigInteger steps) throws IOException {
        return invoke(wallet, method, params, value, steps, null, null);
    }

    public Bytes invoke(Wallet wallet, String method, RpcObject params, BigInteger value,
                        BigInteger steps, BigInteger timestamp, BigInteger nonce) throws IOException {
        Transaction tx = getTransaction(wallet, method, params, value, steps, timestamp, nonce);
        return this.txHandler.invoke(wallet, tx);
    }

    private Transaction getTransaction(Wallet wallet, String method, RpcObject params, BigInteger value,
                                       BigInteger steps, BigInteger timestamp, BigInteger nonce) {
        TransactionBuilder.Builder builder = TransactionBuilder.newBuilder()
                .nid(getNetworkId())
                .from(wallet.getAddress())
                .to(getAddress())
                .stepLimit(steps);

        if ((value != null) && value.bitLength() != 0) {
            builder.value(value);
        }
        if ((timestamp != null) && timestamp.bitLength() != 0) {
            builder.timestamp(timestamp);
        }
        if (nonce != null) {
            builder.nonce(nonce);
        }

        Transaction tx;
        if (params != null) {
            tx = builder.call(method).params(params).build();
        } else {
            tx = builder.call(method).build();
        }
        return tx;
    }

    public TransactionResult invokeAndWaitResult(Wallet wallet, String method, RpcObject params)
            throws ResultTimeoutException, IOException {
        return invokeAndWaitResult(wallet, method, params, null, Constants.DEFAULT_STEPS);
    }

    public TransactionResult invokeAndWaitResult(Wallet wallet, String method, RpcObject params,
                                                 BigInteger steps)
            throws ResultTimeoutException, IOException {
        return invokeAndWaitResult(wallet, method, params, null, steps);
    }

    public TransactionResult invokeAndWaitResult(Wallet wallet, String method, RpcObject params,
                                                 BigInteger value, BigInteger steps)
            throws ResultTimeoutException, IOException {
        Bytes txHash = this.invoke(wallet, method, params, value, steps);
        return getResult(txHash);
    }

    public TransactionResult getResult(Bytes txHash) throws ResultTimeoutException, IOException {
        return getResult(txHash, Constants.DEFAULT_WAITING_TIME);
    }

    public TransactionResult getResult(Bytes txHash, long waiting) throws ResultTimeoutException, IOException {
        return this.txHandler.getResult(txHash, waiting);
    }

    public Address getAddress() {
        return this.address;
    }

    public BigInteger getNetworkId() {
        return txHandler.getNetworkId();
    }

    @Override
    public String toString() {
        return "SCORE(" + getAddress().toString() + ")";
    }
}
