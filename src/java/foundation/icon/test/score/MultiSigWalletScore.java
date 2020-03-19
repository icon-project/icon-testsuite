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

import foundation.icon.icx.*;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.IconAmount;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.Constants;
import foundation.icon.test.Utils;

import java.io.IOException;
import java.math.BigInteger;

public class MultiSigWalletScore {
    private static final BigInteger STATUS_SUCCESS = BigInteger.ONE;

    private final IconService iconService;
    private final Address scoreAddress;

    public MultiSigWalletScore(IconService iconService, Address scoreAddress) {
        this.iconService = iconService;
        this.scoreAddress = scoreAddress;
    }

    private Bytes sendTransaction(Wallet fromWallet, String function, RpcObject params) throws IOException {
        Transaction transaction = TransactionBuilder.newBuilder()
                .nid(Constants.NETWORK_ID)
                .from(fromWallet.getAddress())
                .to(scoreAddress)
                .stepLimit(new BigInteger("10000000"))
                .timestamp(Utils.getMicroTime())
                .nonce(new BigInteger("1"))
                .call(function)
                .params(params)
                .build();

        SignedTransaction signedTransaction = new SignedTransaction(transaction, fromWallet);
        Bytes txHash = iconService.sendTransaction(signedTransaction).execute();

        TransactionResult result = Utils.getTransactionResult(iconService, txHash);
        if (!STATUS_SUCCESS.equals(result.getStatus())) {
            throw new IOException("Failed to execute sendTransaction.");
        }
        return txHash;
    }

    public Bytes submitIcxTransaction(Wallet fromWallet, Address dest, long value, String description) throws IOException {
        BigInteger icx = IconAmount.of(BigInteger.valueOf(value), IconAmount.Unit.ICX).toLoop();
        RpcObject params = new RpcObject.Builder()
                .put("_destination", new RpcValue(dest))
                .put("_value", new RpcValue(icx))
                .put("_description", new RpcValue(description))
                .build();
        return sendTransaction(fromWallet, "submitTransaction", params);
    }

    public Bytes confirmTransaction(Wallet fromWallet, BigInteger txId) throws IOException {
        RpcObject params = new RpcObject.Builder()
                .put("_transactionId", new RpcValue(txId))
                .build();
        return sendTransaction(fromWallet, "confirmTransaction", params);
    }

    public Bytes addWalletOwner(Wallet fromWallet, Address newOwner, String description) throws IOException {
        String methodParams = String.format("[{\"name\": \"_walletOwner\", \"type\": \"Address\", \"value\": \"%s\"}]", newOwner);
        RpcObject params = new RpcObject.Builder()
                .put("_destination", new RpcValue(scoreAddress))
                .put("_method", new RpcValue("addWalletOwner"))
                .put("_params", new RpcValue(methodParams))
                .put("_description", new RpcValue(description))
                .build();
        return sendTransaction(fromWallet, "submitTransaction", params);
    }

    public Bytes replaceWalletOwner(Wallet fromWallet, Address oldOwner, Address newOwner, String description) throws IOException {
        String methodParams = String.format(
                "[{\"name\": \"_walletOwner\", \"type\": \"Address\", \"value\": \"%s\"},"
                + "{\"name\": \"_newWalletOwner\", \"type\": \"Address\", \"value\": \"%s\"}]", oldOwner, newOwner);
        RpcObject params = new RpcObject.Builder()
                .put("_destination", new RpcValue(scoreAddress))
                .put("_method", new RpcValue("replaceWalletOwner"))
                .put("_params", new RpcValue(methodParams))
                .put("_description", new RpcValue(description))
                .build();
        return sendTransaction(fromWallet, "submitTransaction", params);
    }

    public Bytes changeRequirement(Wallet fromWallet, int required, String description) throws IOException {
        String methodParams = String.format("[{\"name\": \"_required\", \"type\": \"int\", \"value\": \"%d\"}]", required);
        RpcObject params = new RpcObject.Builder()
                .put("_destination", new RpcValue(scoreAddress))
                .put("_method", new RpcValue("changeRequirement"))
                .put("_params", new RpcValue(methodParams))
                .put("_description", new RpcValue(description))
                .build();
        return sendTransaction(fromWallet, "submitTransaction", params);
    }
}
