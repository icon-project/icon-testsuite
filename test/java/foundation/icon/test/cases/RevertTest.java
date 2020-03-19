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

package foundation.icon.test.cases;

import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.test.Constants;
import foundation.icon.test.TransactionFailureException;
import foundation.icon.test.Utils;
import foundation.icon.test.score.StepCounterScore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;

public class RevertTest {
    @Test
    public void testAll() throws IOException, TransactionFailureException {
        IconService iconService = new IconService(
                new HttpProvider(Constants.ENDPOINT_URL_LOCAL));

        System.out.println("[#] Prepare wallets");

        KeyWallet godWallet = Utils.readWalletFromFile("/ws/tests/keystore_test1.json", "test1_Account");
        KeyWallet ownerWallet = Utils.createAndStoreWallet();
        String ownerBalance = "1000";
        Utils.transferIcx(iconService, godWallet, ownerWallet.getAddress(), "1000");
        Utils.ensureIcxBalance(iconService, ownerWallet.getAddress(), 0, Long.parseLong(ownerBalance));

        System.out.println("[>] Deploy SCORE1");
        StepCounterScore score1 = StepCounterScore.mustDeploy(iconService,
                ownerWallet, "./step_counter.zip");
        System.out.println("[<] StepCounter deployed:"+score1);

        System.out.println("[>] Deploy SCORE2");
        StepCounterScore score2 = StepCounterScore.mustDeploy(iconService,
                ownerWallet, "./step_counter.zip");
        System.out.println("[<] StepCounter deployed:"+score2);

        TransactionResult txr;
        BigInteger v1, v2, v, v1new, v2new;

        System.out.println("[>] "+score1+".getStep()");
        v1 = score1.getStep(ownerWallet.getAddress());
        System.out.println("[<] getStep() => "+v1);
        System.out.println("[>] "+score2+".getStep()");
        v2 = score2.getStep(ownerWallet.getAddress());
        System.out.println("[<] getStep() => "+v2);

        v = v1.add(BigInteger.ONE);

        System.out.println("[>] "+score2+".setStepOf("+score1+","+v+")");
        txr = score2.setStepOf(ownerWallet, score1.getAddress(), v);
        System.out.println("[<] Result:"+txr);
        if (!BigInteger.ONE.equals(txr.getStatus())) {
            System.err.println("[!] It should SUCCESS");
            return;
        }
        v1 = score1.getStep(ownerWallet.getAddress());
        if (!v.equals(v1)) {
            System.err.println("[!] "+score1+".getValue() =>"+v1+" expect="+v);
            return;
        }

        System.out.println("[>] "+score2+".setStepOf("+score1+","+v+")");
        txr = score2.setStepOf(ownerWallet, score1.getAddress(), v);
        System.out.println("[<] Result:"+txr);
        if (BigInteger.ONE.equals(txr.getStatus())) {
            System.err.println("[!] It should FAIL");
            return;
        }

        System.out.println("[>] "+score1+".getStep()");
        v1 = score1.getStep(ownerWallet.getAddress());
        System.out.println("[<] getStep() => "+v1);
        System.out.println("[>] "+score2+".getStep()");
        v2 = score2.getStep(ownerWallet.getAddress());
        System.out.println("[<] getStep() => "+v2);

        v = v.add(BigInteger.ONE);

        System.out.println("[>] "+score1+".trySetStepWith("+score2+","+v+")");
        txr = score1.trySetStepWith(ownerWallet, score2.getAddress(),v);
        System.out.println("[<] Result:"+txr);
        if (BigInteger.ZERO.equals(txr.getStatus())) {
            System.err.println("[!] It should SUCCESS");
            return;
        }

        v2new = score2.getStep(ownerWallet.getAddress());
        if (!v2.equals(v2new)) {
            System.err.println("[!] "+score2+".getValue()=>"+v2new+" expect="+v2);
            return;
        }

        v1new = score1.getStep(ownerWallet.getAddress());
        if (!v.equals(v1new)) {
            System.err.println("[!] "+score1+".getValue()=>"+v1new+" expect="+v);
            return;
        }
        v1 = v1new;

        System.out.println("SUCCESS");
    }
}
