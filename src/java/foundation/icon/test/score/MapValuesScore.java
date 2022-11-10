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

import foundation.icon.icx.Wallet;
import foundation.icon.test.ResultTimeoutException;
import foundation.icon.test.TransactionFailureException;
import foundation.icon.test.TransactionHandler;

import java.io.IOException;

public class MapValuesScore extends JavaScore {
    public MapValuesScore(Score other) {
        super(other);
    }

    public static MapValuesScore mustDeploy(TransactionHandler txHandler, Wallet wallet)
            throws TransactionFailureException, IOException, ResultTimeoutException {
        return new MapValuesScore(deployScore(txHandler, wallet,
                new Class<?>[]{contract.MapValues.class}, null));
    }
}
