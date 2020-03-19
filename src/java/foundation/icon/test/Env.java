/*
 * Copyright 2020 ICON Foundation
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

package foundation.icon.test;

import foundation.icon.icx.Wallet;

import java.io.IOException;

public class Env {
    public static final Log LOG = Log.getGlobal();
    private static final String LOCAL_URI = "http://localhost:9000";

    public static Chain getDefaultChain() throws IOException {
        Wallet godWallet = Utils.readWalletFromFile("/ws/tests/keystore_test1.json", "test1_Account");
        return new Chain(3, godWallet);
    }

    public static class Chain {
        public final int networkId;
        public final Wallet godWallet;

        public Chain(int networkId, Wallet godWallet) {
            this.networkId = networkId;
            this.godWallet = godWallet;
        }

        public String getEndpointURL(int v) {
            return LOCAL_URI + "/api/v" + v + "/";
        }
    }
}
