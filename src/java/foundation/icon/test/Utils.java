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

package foundation.icon.test;

import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.data.TransactionResult.EventLog;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import static foundation.icon.test.Env.LOG;

public class Utils {
    public static void ensureIcxBalance(TransactionHandler txHandler, Address address,
                                        BigInteger oldVal, BigInteger newVal) throws Exception {
        long limitTime = System.currentTimeMillis() + Constants.DEFAULT_WAITING_TIME;
        while (true) {
            BigInteger icxBalance = txHandler.getBalance(address);
            String msg = "ICX balance of " + address + ": " + icxBalance;
            if (icxBalance.equals(oldVal)) {
                if (limitTime < System.currentTimeMillis()) {
                    throw new ResultTimeoutException();
                }
                try {
                    // wait until block confirmation
                    LOG.debug(msg + "; Retry in 1 sec.");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else if (icxBalance.equals(newVal)) {
                LOG.info(msg);
                break;
            } else {
                throw new IOException(String.format("ICX balance mismatch: expected <%s>, but was <%s>",
                        newVal, icxBalance));
            }
        }
    }

    public static EventLog findEventLogWithFuncSig(TransactionResult result, Address scoreAddress, String funcSig) {
        List<EventLog> eventLogs = result.getEventLogs();
        for (EventLog event : eventLogs) {
            if (event.getScoreAddress().equals(scoreAddress.toString())) {
                String signature = event.getIndexed().get(0).asString();
                if (funcSig.equals(signature)) {
                    return event;
                }
            }
        }
        return null;
    }
}
