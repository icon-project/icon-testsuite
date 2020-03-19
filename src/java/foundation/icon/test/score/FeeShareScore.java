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

import foundation.icon.icx.IconService;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;

import java.io.IOException;
import java.math.BigInteger;

public class FeeShareScore extends Score {
    final static BigInteger Steps = BigInteger.valueOf(3).multiply(BigInteger.TEN.pow(6));

    private final Wallet wallet;

    public FeeShareScore(IconService service, Wallet wallet, Address target) {
        super(service, target);
        this.wallet = wallet;
    }

    public String getValue() throws IOException {
        RpcItem res = this.call("getValue", null);
        return res.asString();
    }

    public TransactionResult addToWhitelist(Address address, int proportion) throws IOException {
        return invokeAndWaitResult(wallet,
                "addToWhitelist",
                (new RpcObject.Builder())
                        .put("address", new RpcValue(address))
                        .put("proportion", new RpcValue(BigInteger.valueOf(proportion)))
                        .build(),
                null, Steps);
    }

    public TransactionResult setValue(String value) throws IOException {
        return invokeAndWaitResult(wallet,
                "setValue",
                (new RpcObject.Builder())
                        .put("value", new RpcValue(value))
                        .build(),
                null, Steps);
    }
}
