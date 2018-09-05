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

import foundation.icon.icx.*;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.IconAmount;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;

import java.io.IOException;
import java.math.BigInteger;

class TokenScore {
    private final IconService iconService;
    private final Address scoreAddress;

    TokenScore(IconService iconService, Address scoreAddress) {
        this.iconService = iconService;
        this.scoreAddress = scoreAddress;

        //TODO: check if this is really a token SCORE that conforms to IRC2
    }

    RpcItem balanceOf(Address owner) throws IOException {
        RpcObject params = new RpcObject.Builder()
                .put("_owner", new RpcValue(owner))
                .build();
        Call<RpcItem> call = new Call.Builder()
                .from(owner)
                .to(scoreAddress)
                .method("balanceOf")
                .params(params)
                .build();
        return iconService.call(call).execute();
    }

    Bytes transfer(Wallet fromWallet, Address toAddress, String value) throws IOException {
        RpcObject params = new RpcObject.Builder()
                .put("_to", new RpcValue(toAddress))
                .put("_value", new RpcValue(IconAmount.of(value, 18).toLoop()))
                .build();

        long timestamp = System.currentTimeMillis() * 1000L;
        Transaction transaction = TransactionBuilder.of(SampleTokenTest.NETWORK_ID)
                .from(fromWallet.getAddress())
                .to(scoreAddress)
                .stepLimit(new BigInteger("2000000"))
                .timestamp(new BigInteger(Long.toString(timestamp)))
                .nonce(new BigInteger("1"))
                .call("transfer")
                .params(params)
                .build();

        SignedTransaction signedTransaction = new SignedTransaction(transaction, fromWallet);
        return iconService.sendTransaction(signedTransaction).execute();
    }
}
