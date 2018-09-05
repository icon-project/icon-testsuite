/*
 * Copyright (c) 2018 ICON Foundation
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

package foundation.icon.tests;

import foundation.icon.icx.*;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;

import java.io.IOException;
import java.math.BigInteger;

class CrowdSaleScore {
    private final IconService iconService;
    private final Address scoreAddress;

    CrowdSaleScore(IconService iconService, Address scoreAddress) {
        this.iconService = iconService;
        this.scoreAddress = scoreAddress;
    }

    private Bytes sendTransaction(Wallet fromWallet, Address scoreAddress, String function) throws IOException {
        long timestamp = System.currentTimeMillis() * 1000L;
        Transaction transaction = TransactionBuilder.of(SampleTokenTest.NETWORK_ID)
                .from(fromWallet.getAddress())
                .to(scoreAddress)
                .stepLimit(new BigInteger("2000000"))
                .timestamp(new BigInteger(Long.toString(timestamp)))
                .nonce(new BigInteger("1"))
                .call(function)
                .build();

        SignedTransaction signedTransaction = new SignedTransaction(transaction, fromWallet);
        return iconService.sendTransaction(signedTransaction).execute();
    }

    Bytes checkGoalReached(Wallet fromWallet) throws IOException {
        return sendTransaction(fromWallet, scoreAddress, "check_goal_reached");
    }

    Bytes safeWithdrawal(Wallet fromWallet) throws IOException {
        return sendTransaction(fromWallet, scoreAddress, "safe_withdrawal");
    }
}
