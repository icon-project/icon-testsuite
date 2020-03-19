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
import foundation.icon.test.TransactionFailureException;

import java.io.IOException;
import java.math.BigInteger;

public class StepCounterScore extends Score {
    private static final BigInteger STEPS = BigInteger.valueOf(200000);

    public static StepCounterScore mustDeploy(IconService service, Wallet wallet)
            throws IOException, TransactionFailureException
    {
        return new StepCounterScore(
                service,
                Score.mustDeploy(service, wallet, "step_counter", null)
        );
    }

    StepCounterScore(IconService service, Address target) {
        super(service, target);
    }

    public TransactionResult increaseStep(Wallet wallet) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "increaseStep", null, null, STEPS);
    }

    public TransactionResult setStep(Wallet wallet, BigInteger step) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "setStep",
                (new RpcObject.Builder())
                    .put("step", new RpcValue(step))
                    .build(),
                null, STEPS);
    }

    public TransactionResult resetStep(Wallet wallet, BigInteger step) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "resetStep",
                (new RpcObject.Builder())
                        .put("step", new RpcValue(step))
                        .build(),
                null, STEPS);
    }

    public TransactionResult setStepOf(Wallet wallet, Address target, BigInteger step) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "setStepOf",
                (new RpcObject.Builder())
                    .put("step", new RpcValue(step))
                    .put("addr", new RpcValue(target))
                    .build(),
                null, STEPS);
    }

    public TransactionResult trySetStepWith(Wallet wallet, Address target, BigInteger step) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "trySetStepWith",
                (new RpcObject.Builder())
                        .put("step", new RpcValue(step))
                        .put("addr", new RpcValue(target))
                        .build(),
                null, STEPS);
    }

    public BigInteger getStep(Address from) throws IOException {
        RpcItem res = this.call("getStep", null);
        return res.asInteger();
    }
}
