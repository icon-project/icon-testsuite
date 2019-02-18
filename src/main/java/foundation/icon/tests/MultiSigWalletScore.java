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

import foundation.icon.icx.*;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;

import java.io.IOException;
import java.math.BigInteger;

class MultiSigWalletScore {
    private final IconService iconService;
    private final Address scoreAddress;

    MultiSigWalletScore(IconService iconService, Address scoreAddress) {
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
        return iconService.sendTransaction(signedTransaction).execute();
    }

    Bytes submitTransaction(Wallet fromWallet, RpcObject params) throws IOException {
        return sendTransaction(fromWallet, "submitTransaction", params);
    }

    Bytes confirmTransaction(Wallet fromWallet, BigInteger txId) throws IOException {
        RpcObject params = new RpcObject.Builder()
                .put("_transactionId", new RpcValue(txId))
                .build();
        return sendTransaction(fromWallet, "confirmTransaction", params);
    }
}
