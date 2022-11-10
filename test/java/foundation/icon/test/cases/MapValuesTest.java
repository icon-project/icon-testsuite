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

package foundation.icon.test.cases;

import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.test.Env;
import foundation.icon.test.TestBase;
import foundation.icon.test.TransactionHandler;
import foundation.icon.test.score.ChainScore;
import foundation.icon.test.score.MapValuesScore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static foundation.icon.test.Env.LOG;

public class MapValuesTest extends TestBase {
    private static final int FIXED_REVISION = 20;
    private static TransactionHandler txHandler;
    private static KeyWallet ownerWallet;

    @BeforeAll
    static void setup() throws Exception {
        Env.Chain chain = Env.getDefaultChain();
        IconService iconService = new IconService(new HttpProvider(chain.getEndpointURL(3)));
        txHandler = new TransactionHandler(iconService, chain);
        ownerWallet = KeyWallet.create();
        // transfer initial test icx to owner address
        transferAndCheckResult(txHandler, ownerWallet.getAddress(), ICX.multiply(BigInteger.valueOf(1000)));
    }

    @AfterAll
    static void shutdown() throws Exception {
        txHandler.refundAll(ownerWallet);
    }

    @Test
    public void runTest() throws Exception {
        LOG.infoEntering("deploy", "MapValues");
        var score = MapValuesScore.mustDeploy(txHandler, ownerWallet);
        LOG.info("scoreAddr = " + score.getAddress());
        LOG.infoExiting();

        LOG.infoEntering("check revision");
        ChainScore chainScore = new ChainScore(txHandler);
        int revision = chainScore.getRevision();
        LOG.info("revision = " + revision);
        LOG.infoExiting();

        LOG.infoEntering("invoke tests");
        if (revision < FIXED_REVISION) {
            LOG.info("- valuesInNotFixMapValues");
            assertSuccess(score.invokeAndWaitResult(ownerWallet, "valuesInNotFixMapValues", null));
        } else {
            LOG.info("- valuesInFixMapValues");
            assertSuccess(score.invokeAndWaitResult(ownerWallet, "valuesInFixMapValues", null));
        }
        LOG.info("- keySet");
        assertSuccess(score.invokeAndWaitResult(ownerWallet, "keySet", null));
        LOG.infoExiting();
    }
}
