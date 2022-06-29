/*
 * Copyright 2022 ICON Foundation
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

import foundation.icon.ee.tooling.deploy.OptimizedJarBuilder;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.test.ResultTimeoutException;
import foundation.icon.test.TransactionFailureException;
import foundation.icon.test.TransactionHandler;
import foundation.icon.test.Constants;
import org.aion.avm.utilities.JarBuilder;

import java.io.IOException;

public class JavaScore extends Score {
    public JavaScore(TransactionHandler txHandler, Address scoreAddress) {
        super(txHandler, scoreAddress);
    }

    public JavaScore(Score other) {
        super(other);
    }

    protected static Score deployScore(TransactionHandler txHandler, Wallet owner, Class<?>[] classes, RpcObject params)
            throws IOException, TransactionFailureException, ResultTimeoutException {
        byte[] jar = makeJar(classes[0].getName(), classes);
        return txHandler.getScore(txHandler.doDeploy(owner, jar, Constants.SYSTEM_ADDRESS,
                params, null, Constants.CONTENT_TYPE_JAVA));
    }

    protected Bytes updateScore(Wallet owner, Class<?>[] classes, RpcObject params) throws IOException {
        byte[] jar = makeJar(classes[0].getName(), classes);
        return getTxHandler().doDeploy(owner, jar, getAddress(), params, null, Constants.CONTENT_TYPE_JAVA);
    }

    public static byte[] makeJar(String name, Class<?>[] classes) {
        byte[] jarBytes = JarBuilder.buildJarForExplicitMainAndClasses(name, classes);
        return new OptimizedJarBuilder(false, jarBytes, true)
                .withUnreachableMethodRemover()
                .withRenamer()
                .getOptimizedBytes();
    }
}
