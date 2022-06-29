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

import foundation.icon.icx.Wallet;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.Constants;
import foundation.icon.test.ResultTimeoutException;
import foundation.icon.test.TransactionFailureException;
import foundation.icon.test.TransactionHandler;

import java.io.IOException;

public class HelloWorldScore extends JavaScore {
    public HelloWorldScore(Score other) {
        super(other);
    }

    public static HelloWorldScore install(TransactionHandler txHandler, Wallet wallet)
            throws TransactionFailureException, ResultTimeoutException, IOException {
        return install(txHandler, wallet, Constants.CONTENT_TYPE_PYTHON);
    }

    public static HelloWorldScore install(TransactionHandler txHandler, Wallet wallet, String contentType)
            throws TransactionFailureException, ResultTimeoutException, IOException {
        RpcObject params = new RpcObject.Builder()
                .put("name", new RpcValue("HelloWorld"))
                .build();
        if (contentType.equals(Constants.CONTENT_TYPE_PYTHON)) {
            return new HelloWorldScore(txHandler.deploy(wallet, getFilePath("hello_world"), params));
        } else if (contentType.equals(Constants.CONTENT_TYPE_JAVA)) {
            return new HelloWorldScore(JavaScore.deployScore(txHandler, wallet,
                    new Class<?>[]{contract.HelloWorld.class}, params));
        } else {
            throw new IllegalArgumentException("Unknown content type");
        }
    }
}
