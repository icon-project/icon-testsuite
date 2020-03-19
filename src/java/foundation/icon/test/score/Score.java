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
import foundation.icon.icx.IconService;
import foundation.icon.icx.SignedTransaction;
import foundation.icon.icx.Transaction;
import foundation.icon.icx.TransactionBuilder;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.test.Constants;
import foundation.icon.test.TransactionFailureException;
import foundation.icon.test.Utils;
import foundation.icon.test.util.ZipFile;

import java.io.IOException;
import java.math.BigInteger;

public class Score {
    private static final String SCORE_ROOT = "./test/scores/";
    private static final BigInteger STATUS_SUCCESS = BigInteger.ONE;

    private IconService service;
    private Address target;

    protected Score(IconService service, Address target) {
        this.service = service;
        this.target = target;
    }

    public static TransactionResult deployAndWaitResult(IconService service, Wallet wallet, String pkgName, RpcObject params)
            throws IOException
    {
        byte[] content = ZipFile.zipContent(SCORE_ROOT + pkgName);
        Bytes txHash = Utils.deployScore(service, wallet, content, params);
        return Utils.getTransactionResult(service, txHash);
    }

    public static Address mustDeploy(IconService service, Wallet wallet, String pkgName,
                                     RpcObject params)
        throws IOException, TransactionFailureException
    {
        TransactionResult result = deployAndWaitResult(service, wallet, pkgName, params);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new TransactionFailureException(result.getFailure());
        }
        return new Address(result.getScoreAddress());
    }

    public RpcItem call(String method, RpcObject params)
            throws IOException {
        if (params==null) {
            params = new RpcObject.Builder().build();
        }
        Call<RpcItem> call = new Call.Builder()
                .to(this.target)
                .method(method)
                .params(params)
                .build();
        return this.service.call(call).execute();
    }

    public Bytes invoke(Wallet wallet, String method,
            RpcObject params, BigInteger value, BigInteger steps)
            throws IOException
    {
        TransactionBuilder.Builder builder = TransactionBuilder.newBuilder()
                .nid(Constants.NETWORK_ID)
                .from(wallet.getAddress())
                .to(this.target)
                .stepLimit(steps);

        if ((value != null) && value.bitLength()!=0) {
            builder = builder.value(value);
        }

        Transaction t = null;
        if (params!=null) {
            t = builder.call(method).params(params).build();
        } else {
            t = builder.call(method).build();
        }

        return this.service
                .sendTransaction(new SignedTransaction(t, wallet))
                .execute();
    }

    public TransactionResult invokeAndWaitResult(Wallet wallet, String method,
            RpcObject params, BigInteger value, BigInteger steps)
            throws IOException {
        Bytes txHash = this.invoke(wallet, method, params, value, steps);
        return waitResult(txHash);
    }

    public Bytes transfer(Wallet wallet, Address to, BigInteger value, BigInteger steps)
            throws IOException
    {
        Transaction tx = TransactionBuilder.newBuilder()
                .nid(Constants.NETWORK_ID)
                .from(wallet.getAddress())
                .to(this.target)
                .value(value)
                .stepLimit(steps)
                .build();
        SignedTransaction signed =
                new SignedTransaction(tx, wallet);
        return this.service
                .sendTransaction(signed).execute();
    }

    public TransactionResult transferAndWaitResult(Wallet wallet, Address to,
                                             BigInteger value, BigInteger steps
    ) throws IOException, TransactionFailureException {
        Bytes txHash = this.transfer(wallet, to, value, steps);
        return waitResult(txHash);
    }

    public TransactionResult waitResult(Bytes txHash) throws IOException {
        return Utils.getTransactionResult(this.service, txHash);
    }

    public Address getAddress() {
        return this.target;
    }

    @Override
    public String toString() {
        return "SCORE("+this.target.toString()+")";
    }
}
