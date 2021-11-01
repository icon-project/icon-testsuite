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

package foundation.icon.test.cases;

import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.test.Env;
import foundation.icon.test.TestBase;
import foundation.icon.test.TransactionHandler;
import foundation.icon.test.score.SampleTokenScore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static foundation.icon.test.Env.LOG;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SampleTokenTest extends TestBase {
    private static TransactionHandler txHandler;
    private static KeyWallet ownerWallet;

    @BeforeAll
    static void setup() throws Exception {
        Env.Chain chain = Env.getDefaultChain();
        IconService iconService = new IconService(new HttpProvider(chain.getEndpointURL(3)));
        txHandler = new TransactionHandler(iconService, chain);
        ownerWallet = KeyWallet.create();

        // deposit initial balance for the owner
        BigInteger amount = ICX.multiply(BigInteger.valueOf(100));
        txHandler.transfer(ownerWallet.getAddress(), amount);
        ensureIcxBalance(txHandler, ownerWallet.getAddress(), BigInteger.ZERO, amount);
    }

    @AfterAll
    static void shutdown() throws Exception {
        txHandler.refundAll(ownerWallet);
    }

    @Test
    public void runTest() throws Exception {
        KeyWallet calleeWallet = KeyWallet.create();

        // 1. deploy
        BigInteger decimals = BigInteger.valueOf(18);
        BigInteger initialSupply = BigInteger.valueOf(1000);
        SampleTokenScore tokenScore = SampleTokenScore.mustDeploy(txHandler, ownerWallet,
                decimals, initialSupply);

        // 2. balanceOf
        LOG.infoEntering("balanceOf", "owner (initial)");
        BigInteger oneToken = BigInteger.TEN.pow(decimals.intValue());
        BigInteger totalSupply = oneToken.multiply(initialSupply);
        BigInteger bal = tokenScore.balanceOf(ownerWallet.getAddress());
        LOG.info("expected (" + totalSupply + "), got (" + bal + ")");
        assertEquals(totalSupply, bal);
        LOG.infoExiting();

        // 3. transfer #1
        LOG.infoEntering("transfer", "#1");
        TransactionResult result = tokenScore.transfer(ownerWallet, calleeWallet.getAddress(), oneToken);
        tokenScore.ensureTransfer(result, ownerWallet.getAddress(), calleeWallet.getAddress(), oneToken, null);
        LOG.infoExiting();

        // 3.1 transfer #2
        LOG.infoEntering("transfer", "#2");
        BigInteger two = oneToken.add(oneToken);
        byte[] data = "Hello".getBytes();
        result = tokenScore.transfer(ownerWallet, calleeWallet.getAddress(), two, data);
        assertSuccess(result);
        tokenScore.ensureTransfer(result, ownerWallet.getAddress(), calleeWallet.getAddress(), two, data);
        LOG.infoExiting();

        // 4. check balance of callee
        LOG.infoEntering("balanceOf", "callee");
        BigInteger expected = oneToken.add(two);
        bal = tokenScore.balanceOf(calleeWallet.getAddress());
        LOG.info("expected (" + expected + "), got (" + bal + ")");
        assertEquals(expected, bal);
        LOG.infoExiting();

        // 5. check balance of owner
        LOG.infoEntering("balanceOf", "owner");
        expected = totalSupply.subtract(expected);
        bal = tokenScore.balanceOf(ownerWallet.getAddress());
        LOG.info("expected (" + expected + "), got (" + bal + ")");
        assertEquals(expected, bal);
        LOG.infoExiting();
    }

    @Test
    public void updatePythonToJava() throws Exception {
        // 1. deploy Python SCORE first
        BigInteger decimals = BigInteger.valueOf(18);
        BigInteger initialSupply = BigInteger.valueOf(1000);
        SampleTokenScore tokenScore = SampleTokenScore.mustDeploy(txHandler, ownerWallet,
                decimals, initialSupply);

        BigInteger oneToken = BigInteger.TEN.pow(decimals.intValue());
        BigInteger totalSupply = oneToken.multiply(initialSupply);

        // 2. transfer before update
        LOG.infoEntering("transfer", "10 tokens before update");
        KeyWallet calleeWallet = KeyWallet.create();
        BigInteger tenToken = oneToken.multiply(BigInteger.TEN);
        TransactionResult result = tokenScore.transfer(ownerWallet, calleeWallet.getAddress(), tenToken);
        tokenScore.ensureTransfer(result, ownerWallet.getAddress(), calleeWallet.getAddress(), tenToken, null);
        LOG.infoExiting();

        // 3. update to Java SCORE
        LOG.infoEntering("deploy", "update to Java SCORE");
        var hash = tokenScore.updateToJavaScore(ownerWallet);
        assertSuccess(tokenScore.getResult(hash));
        LOG.infoExiting();

        // 4. check if name, symbol and totalSupply are unchanged
        assertEquals("MySampleToken", tokenScore.call("name", null).asString());
        assertEquals("MST", tokenScore.call("symbol", null).asString());
        assertEquals(totalSupply, tokenScore.call("totalSupply", null).asInteger());

        // 4. check balance of owner
        LOG.infoEntering("balanceOf", "owner after update");
        BigInteger expected = totalSupply.subtract(tenToken);
        BigInteger bal = tokenScore.balanceOf(ownerWallet.getAddress());
        LOG.info("expected (" + expected + "), got (" + bal + ")");
        assertEquals(expected, bal);
        LOG.infoExiting();
    }
}
