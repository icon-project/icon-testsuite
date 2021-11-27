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
import foundation.icon.icx.Transaction;
import foundation.icon.icx.TransactionBuilder;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.Constants;
import foundation.icon.test.Env;
import foundation.icon.test.TestBase;
import foundation.icon.test.TransactionFailureException;
import foundation.icon.test.TransactionHandler;
import foundation.icon.test.score.ChainScore;
import foundation.icon.test.score.HelloWorldScore;
import foundation.icon.test.score.Score;
import foundation.icon.test.util.ZipFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;

import static foundation.icon.test.Env.LOG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StepTest extends TestBase {
    private static TransactionHandler txHandler;
    private static KeyWallet[] testWallets;

    private static BigInteger STEP_PRICE;
    private static Map<String, BigInteger> STEP_COSTS;
    private static BigInteger SCHEMA;

    private static final int TYPE_INT = 0;
    private static final int TYPE_STR = 1;
    private static final int TYPE_BYTES = 2;
    private static final int TYPE_ADDR = 3;

    @BeforeAll
    static void setup() throws Exception {
        Env.Chain chain = Env.getDefaultChain();
        IconService iconService = new IconService(new HttpProvider(chain.getEndpointURL(3)));
        txHandler = new TransactionHandler(iconService, chain);

        testWallets = new KeyWallet[4];
        Address[] addresses = new Address[testWallets.length];
        for (int i = 0; i < testWallets.length; i++) {
            KeyWallet wallet = KeyWallet.create();
            testWallets[i] = wallet;
            addresses[i] = wallet.getAddress();
        }
        transferAndCheckResult(txHandler, addresses, ICX.multiply(BigInteger.valueOf(50)));

        ChainScore chainScore = new ChainScore(txHandler);
        STEP_PRICE = chainScore.getStepPrice();
        STEP_COSTS = chainScore.getStepCosts();
        SCHEMA = STEP_COSTS.getOrDefault(StepType.SCHEMA.getName(), BigInteger.ZERO);
    }

    @AfterAll
    static void shutdown() throws Exception {
        for (KeyWallet wallet : testWallets) {
            txHandler.refundAll(wallet);
        }
    }

    private static boolean isSchemaZero() {
        return SCHEMA.equals(BigInteger.ZERO);
    }

    private final static EnumSet<StepType> stepCostsV0 = EnumSet.of(
            StepType.DEFAULT, StepType.CONTRACT_CALL,
            StepType.CONTRACT_CREATE, StepType.CONTRACT_UPDATE,
            StepType.CONTRACT_DESTRUCT, StepType.CONTRACT_SET,
            StepType.GET, StepType.SET, StepType.REPLACE, StepType.DELETE,
            StepType.INPUT, StepType.EVENTLOG, StepType.APICALL
    );

    private final static EnumSet<StepType> stepCostsV1 = EnumSet.of(
            StepType.SCHEMA, StepType.DEFAULT, StepType.CONTRACT_CALL,
            StepType.CONTRACT_CREATE, StepType.CONTRACT_UPDATE_V1, StepType.CONTRACT_SET_V1,
            StepType.GET_V1, StepType.SET, StepType.DELETE, StepType.LOG,
            StepType.GET_BASE, StepType.SET_BASE, StepType.DELETE_BASE, StepType.LOG_BASE,
            StepType.INPUT, StepType.APICALL
    );

    private enum StepType {
        // Legacy
        DEFAULT("default", 100000),
        CONTRACT_CALL("contractCall", 25000),
        CONTRACT_CREATE("contractCreate", 1000000000),
        CONTRACT_UPDATE("contractUpdate", 1600000000),
        CONTRACT_DESTRUCT("contractDestruct", -70000),
        CONTRACT_SET("contractSet", 30000),
        GET("get", 0),
        SET("set", 320),
        REPLACE("replace", 80),
        DELETE("delete", -240),
        INPUT("input", 200),
        EVENTLOG("eventLog", 100),
        APICALL("apiCall", 10000),

        // Updated in schema v1
        SCHEMA("schema", 1),
        CONTRACT_UPDATE_V1("contractUpdate", 1000000000),
        CONTRACT_SET_V1("contractSet", 15000),
        GET_V1("get", 80),
        GET_BASE("getBase", 3000),
        SET_BASE("setBase", 10000),
        DELETE_BASE("deleteBase", 200),
        LOG_BASE("logBase", 5000),
        LOG("log", 100);

        private final String name;
        private final BigInteger steps;

        StepType(String name, long steps) {
            this.name = name;
            this.steps = BigInteger.valueOf(steps);
        }
        public String getName() { return name; }
        public BigInteger getSteps() { return steps; }
    }

    private static class StepTransaction {
        private BigInteger expectedStep;
        private BigInteger usedFee;
        private Address scoreAddr;

        BigInteger expectedStep() {
            return expectedStep;
        }

        BigInteger expectedFee() {
            return expectedStep.multiply(STEP_PRICE);
        }

        BigInteger getUsedFee() {
            return usedFee;
        }

        public Address getScoreAddress() {
            return scoreAddr;
        }

        void addOperation(StepType stepType, int type, String val) {
            long valSize;
            switch (type) {
                case TYPE_INT:
                    int v = Integer.parseInt(val);
                    long bitLen = BigInteger.valueOf(v).bitLength();
                    valSize = bitLen / 8 + 1;
                    // int to byte
                    break;
                case TYPE_BYTES:
                    if (!val.startsWith("0x")) {
                        throw new IllegalArgumentException();
                    }
                    valSize = (val.length() - 2) / 2;
                    break;
                case TYPE_ADDR:
                    if (val.startsWith("hx")) {
                        valSize = 20;
                    } else {
                        valSize = 21;
                    }
                    break;
                case TYPE_STR:
                default:
                    valSize = val.length();
                    break;
            }
            LOG.info("addOperation val : " + val + ", valSize : " + valSize);
            var length = BigInteger.valueOf(valSize);
            BigInteger steps;
            if (isSchemaZero()) {
                steps = stepType.getSteps().multiply(length);
            } else {
                if (StepType.GET.equals(stepType)) {
                    var getBase = STEP_COSTS.getOrDefault(StepType.GET_BASE.getName(), BigInteger.ZERO);
                    steps = getBase.add(StepType.GET_V1.getSteps().multiply(length));
                } else if (StepType.DELETE.equals(stepType)) {
                    var deleteBase = STEP_COSTS.getOrDefault(StepType.DELETE_BASE.getName(), BigInteger.ZERO);
                    steps = deleteBase.add(StepType.DELETE.getSteps().multiply(length));
                } else {
                    steps = calcStoreStep(length, StepType.REPLACE.equals(stepType), length);
                }
            }
            expectedStep = expectedStep.add(steps);
        }

        BigInteger calcTransactionStep(Transaction tx) {
            // default + input * dataLen
            BigInteger stepUsed = StepType.DEFAULT.getSteps();
            long dataLen = 0;
            if (tx.getData() != null) {
                if ("message".equals(tx.getDataType())) {
                    // tx.getData() returns message with no quotes
                    dataLen = tx.getData().asString().getBytes(StandardCharsets.UTF_8).length + 2;
                } else {
                    dataLen = 2; // curly brace
                    RpcObject rpcObject = tx.getData().asObject();
                    for (String key : rpcObject.keySet()) {
                        // Quotes for key(2) + colon(1) + comma(1)
                        dataLen += 4;
                        dataLen += key.length();
                        if ("params".equals(key)) {
                            RpcObject paramObj = rpcObject.getItem(key).asObject();
                            dataLen += 2; // curly brace
                            for (String param : paramObj.keySet()) {
                                dataLen += paramObj.getItem(param).asString().getBytes(StandardCharsets.UTF_8).length;
                                dataLen += param.getBytes(StandardCharsets.UTF_8).length;
                                // Quotes for key(2) + Quotes for value(2) + colon(1) + comma(1)
                                dataLen += 6;
                            }
                            dataLen -= 1; // subtract last comma
                        } else {
                            dataLen += rpcObject.getItem(key).asString().length();
                            dataLen += 2; // add Quotes for value
                        }
                    }
                    dataLen -= 1; // subtract last comma
                }
            }
            return stepUsed.add(StepType.INPUT.getSteps().multiply(BigInteger.valueOf(dataLen)));
        }

        BigInteger calcStoreStep(BigInteger length, boolean update, BigInteger prevLen) {
            var setBase = STEP_COSTS.getOrDefault(StepType.SET_BASE.getName(), BigInteger.ZERO);
            var deleteBase = STEP_COSTS.getOrDefault(StepType.DELETE_BASE.getName(), BigInteger.ZERO);
            var replaceBase = setBase.add(deleteBase).divide(BigInteger.TWO);
            var steps = setBase.add(StepType.SET.getSteps().multiply(length));
            if (update) {
                if (isSchemaZero()) {
                    steps = StepType.REPLACE.getSteps().multiply(length);
                } else {
                    steps = steps.subtract(setBase).add(replaceBase)
                            .add(StepType.DELETE.getSteps().multiply(prevLen));
                }
            }
            return steps;
        }

        BigInteger calcDeployStep(Transaction tx, byte[] content, boolean update) {
            // get the default transaction steps first
            BigInteger stepUsed = calcTransactionStep(tx);
            // if Audit is disabled, the sender must pay steps for executing on_install() or on_update()
            // NOTE: the following calculation can only be applied to hello_world score
            RpcObject params = tx.getData().asObject().getItem("params").asObject();
            String name = params.getItem("name").asString();
            BigInteger nameLength = BigInteger.valueOf(name.length());
            stepUsed = stepUsed.add(calcStoreStep(nameLength, update, BigInteger.TEN));
            // contractCreate or contractUpdate
            // contractSet * codeLen
            BigInteger codeLen = BigInteger.valueOf(content.length);
            if (update) {
                stepUsed = stepUsed.add(STEP_COSTS.get(StepType.CONTRACT_UPDATE.getName()));
            } else {
                stepUsed = stepUsed.add(STEP_COSTS.get(StepType.CONTRACT_CREATE.getName()));
            }
            var contractSet = STEP_COSTS.get(StepType.CONTRACT_SET.getName());
            return stepUsed.add(contractSet.multiply(codeLen));
        }

        BigInteger calcCallStep(Transaction tx) {
            BigInteger stepUsed = calcTransactionStep(tx);
            return StepType.CONTRACT_CALL.getSteps().add(stepUsed);
        }

        BigInteger transfer(KeyWallet from, Address to, BigInteger value, String msg) throws Exception {
            BigInteger prevBal = txHandler.getBalance(from.getAddress());
            TransactionBuilder.Builder builder = TransactionBuilder.newBuilder()
                    .nid(txHandler.getNetworkId())
                    .from(from.getAddress())
                    .to(to)
                    .value(value)
                    .stepLimit(Constants.DEFAULT_STEPS);
            if (msg != null) {
                builder.message(msg);
            }
            Transaction transaction = builder.build();
            this.expectedStep = calcTransactionStep(transaction);
            Bytes txHash = txHandler.invoke(from, transaction);
            assertSuccess(txHandler.getResult(txHash));
            return getUsedFee(from, value, prevBal);
        }

        BigInteger deploy(KeyWallet from, Address to, String contentPath, RpcObject params) throws Exception {
            BigInteger prevBal = txHandler.getBalance(from.getAddress());
            byte[] content = ZipFile.zipContent(contentPath);
            if (to == null) {
                to = Constants.ZERO_ADDRESS;
            }
            Transaction transaction = TransactionBuilder.newBuilder()
                    .nid(txHandler.getNetworkId())
                    .from(from.getAddress())
                    .to(to)
                    .stepLimit(new BigInteger("70000000", 16))
                    .deploy(Constants.CONTENT_TYPE_PYTHON, content)
                    .params(params)
                    .build();
            this.expectedStep = calcDeployStep(transaction, content, to != Constants.ZERO_ADDRESS);
            Bytes txHash = txHandler.invoke(from, transaction);
            TransactionResult result = txHandler.getResult(txHash);
            assertSuccess(result);
            this.scoreAddr = new Address(result.getScoreAddress());
            return getUsedFee(from, BigInteger.ZERO, prevBal);
        }

        BigInteger call(KeyWallet from, Address to, String method, RpcObject params, BigInteger stepLimit) throws Exception {
            BigInteger prevBal = txHandler.getBalance(from.getAddress());
            TransactionBuilder.Builder builder = TransactionBuilder.newBuilder()
                    .nid(txHandler.getNetworkId())
                    .from(from.getAddress())
                    .to(to)
                    .stepLimit(stepLimit);
            Transaction transaction;
            if (params != null) {
                transaction = builder.call(method).params(params).build();
            } else {
                transaction = builder.call(method).build();
            }
            this.expectedStep = calcCallStep(transaction);

            Bytes txHash = txHandler.invoke(from, transaction);
            TransactionResult result = txHandler.getResult(txHash);
            usedFee = getUsedFee(from, BigInteger.ZERO, prevBal);
            if (!Constants.STATUS_SUCCESS.equals(result.getStatus())) {
                LOG.info("Expected " + result.getFailure());
                throw new TransactionFailureException(result.getFailure());
            }
            return usedFee;
        }

        private BigInteger getUsedFee(KeyWallet from, BigInteger value, BigInteger prevBal)
                throws IOException {
            BigInteger bal = txHandler.getBalance(from.getAddress());
            return prevBal.subtract(bal.add(value));
        }
    }

    @Test
    public void compareStepCosts() {
        LOG.infoEntering("compareStepCosts");
        LOG.info("schema: " + SCHEMA);
        var stepCosts = isSchemaZero() ? stepCostsV0 : stepCostsV1;
        assertEquals(stepCosts.size(), STEP_COSTS.size());
        assertTrue(stepCosts.stream().allMatch(e -> e.steps.equals(STEP_COSTS.get(e.name))));
        LOG.infoExiting();
    }

    @Test
    public void testTransfer() throws Exception {
        LOG.infoEntering("testTransfer");
        StepTransaction stx = new StepTransaction();
        LOG.infoEntering("transfer", "simple");
        BigInteger usedFee = stx.transfer(testWallets[0], testWallets[1].getAddress(), ICX, null);
        assertEquals(usedFee, StepType.DEFAULT.getSteps().multiply(STEP_PRICE));
        assertEquals(stx.expectedFee(), usedFee);
        LOG.infoExiting();

        LOG.infoEntering("transfer", "with message");
        usedFee = stx.transfer(testWallets[0], testWallets[2].getAddress(), ICX.multiply(BigInteger.valueOf(2)), "Hello");
        assertTrue(usedFee.compareTo(StepType.DEFAULT.getSteps().multiply(STEP_PRICE)) > 0);
        assertEquals(stx.expectedFee(), usedFee);
        LOG.infoExiting();
        LOG.infoExiting();
    }

    @Test
    public void transferFromScore() throws Exception {
        LOG.infoEntering("transferFromScore");
        LOG.infoEntering("deploy", "Scores");
        Score fromScore = HelloWorldScore.install(txHandler, testWallets[1]);
        Score toScore = HelloWorldScore.install(txHandler, testWallets[2]);
        LOG.infoExiting();
        LOG.infoEntering("deposit", "initial funds");
        transferAndCheckResult(txHandler, fromScore.getAddress(), ICX.multiply(BigInteger.TEN));
        LOG.infoExiting();

        LOG.infoEntering("transfer", "to Score");
        StepTransaction stx = new StepTransaction();
        // get the base fee
        RpcObject params = new RpcObject.Builder()
                .put("to", new RpcValue(toScore.getAddress()))
                .put("amount", new RpcValue(BigInteger.ZERO))
                .build();
        BigInteger baseFee = stx.call(testWallets[1], fromScore.getAddress(), "transferICX", params, Constants.DEFAULT_STEPS);
        BigInteger expectedFee = baseFee.add(STEP_PRICE.multiply(StepType.CONTRACT_CALL.getSteps()));
        // transfer icx and compare with the base fee
        params = new RpcObject.Builder()
                .put("to", new RpcValue(toScore.getAddress()))
                .put("amount", new RpcValue(BigInteger.ONE))
                .build();
        BigInteger usedFee = stx.call(testWallets[1], fromScore.getAddress(), "transferICX", params, Constants.DEFAULT_STEPS);
        assertEquals(expectedFee, usedFee);
        assertEquals(BigInteger.ONE, txHandler.getBalance(toScore.getAddress()));
        LOG.infoExiting();

        LOG.infoEntering("transfer", "to EOA");
        KeyWallet callee = KeyWallet.create();
        params = new RpcObject.Builder()
                .put("to", new RpcValue(callee.getAddress()))
                .put("amount", new RpcValue(BigInteger.ONE))
                .build();
        usedFee = stx.call(testWallets[1], fromScore.getAddress(), "transferICX", params, Constants.DEFAULT_STEPS);
        assertEquals(expectedFee, usedFee);
        assertEquals(BigInteger.ONE, txHandler.getBalance(callee.getAddress()));
        LOG.infoExiting();
        LOG.infoExiting();
    }

    @Test
    public void testDeploy() throws Exception {
        LOG.infoEntering("testDeploy");
        LOG.infoEntering("deploy", "helloWorld");
        RpcObject params = new RpcObject.Builder()
                .put("name", new RpcValue("HelloWorld"))
                .build();
        StepTransaction stx = new StepTransaction();
        BigInteger usedFee = stx.deploy(testWallets[0], null, Score.getFilePath("hello_world"), params);
        LOG.infoExiting();
        assertEquals(stx.expectedFee(), usedFee);

        LOG.infoEntering("update", "helloWorld");
        Address scoreAddr = stx.getScoreAddress();
        params = new RpcObject.Builder()
                .put("name", new RpcValue("Updated HelloWorld"))
                .build();
        stx = new StepTransaction();
        usedFee = stx.deploy(testWallets[0], scoreAddr, Score.getFilePath("hello_world"), params);
        LOG.infoExiting();
        assertEquals(stx.expectedFee(), usedFee);
        LOG.infoExiting();
    }

    private enum VarTest {
        VAR_SET("setToVar", StepType.SET),
        VAR_GET("getFromVar", StepType.GET),
        VAR_REPLACE("setToVar", StepType.REPLACE),
        VAR_EXACT("setToVar", StepType.REPLACE),
        VAR_EDGE("setToVar", StepType.REPLACE),
        VAR_DELETE("delFromVar", StepType.DELETE);

        String[][] params;
        String method;
        StepType stepType;

        VarTest(String method, StepType type) {
            this.method = method;
            this.stepType = type;
        }

        public RpcObject getParams(int type) {
            RpcObject.Builder paramObj = new RpcObject.Builder();
            // set, get, replace, edge, delete
            if (params == null) {
                paramObj = paramObj.put("type", new RpcValue(BigInteger.valueOf(type)));
            } else {
                for (int i = 0; i < type + 1; i++) {
                    paramObj = paramObj.put(params[i][0], new RpcValue(params[i][1]));
                }
            }
            return paramObj.build();
        }
    }

    @Test
    public void testVarDB() throws Exception {
        LOG.infoEntering("testVarDB");
        LOG.infoEntering("deploy", "db_step");
        Score dbScore = txHandler.deploy(testWallets[2], Score.getFilePath("db_step"), null);
        LOG.infoExiting();

        KeyWallet caller = testWallets[3];
        Address scoreAddr = dbScore.getAddress();
        StepTransaction stx = new StepTransaction();
        String[][] initialParams = {
                {"v_int", "128"},
                {"v_str", "tortoise"},
                {"v_bytes", new Bytes("tortoise".getBytes()).toString()},
                {"v_addr", testWallets[0].getAddress().toString()},
        };
        String[][] updatedParams = {
                {"v_int", "821"},
                {"v_str", "esiotrot"},
                {"v_bytes", new Bytes("esiotrot".getBytes()).toString()},
                {"v_addr", testWallets[1].getAddress().toString()},
        };
        BigInteger[] edgeLimit = new BigInteger[4];

        for (VarTest test : VarTest.values()) {
            if (test == VarTest.VAR_SET || test == VarTest.VAR_REPLACE || test == VarTest.VAR_EDGE) {
                test.params = initialParams;
            } else if (test == VarTest.VAR_EXACT) {
                test.params = updatedParams;
            }
            for (int i = 0; i < initialParams.length; i++) {
                String param = initialParams[i][0];
                String val = initialParams[i][1];
                if (test == VarTest.VAR_EXACT) {
                    val = test.params[i][1];
                }
                BigInteger stepLimit = (test == VarTest.VAR_EXACT) ? edgeLimit[i]
                        : (test == VarTest.VAR_EDGE) ? edgeLimit[i].subtract(BigInteger.ONE)
                        : Constants.DEFAULT_STEPS;
                LOG.infoEntering("invoke", "(" + test + ") method=" + test.method + ", param=" + param +
                        ", val=" + val + ", limit=" + stepLimit);
                try {
                    BigInteger usedFee = stx.call(caller, scoreAddr, test.method, test.getParams(i), stepLimit);
                    assertNotEquals(test, VarTest.VAR_EDGE);
                    stx.addOperation(test.stepType, i, val);
                    assertEquals(stx.expectedFee(), usedFee);
                    if (test == VarTest.VAR_REPLACE) {
                        edgeLimit[i] = stx.expectedStep();
                    }
                } catch (TransactionFailureException ex) {
                    if (test != VarTest.VAR_EDGE) {
                        throw ex;
                    }
                    RpcObject callParam = new RpcObject.Builder()
                            .put("type", new RpcValue(BigInteger.valueOf(i)))
                            .build();
                    String dbVal = dbScore.call("readFromVar", callParam).asString();
                    if (i == 0) {
                        dbVal = new BigInteger(dbVal.substring("0x".length()), 16).toString();
                    }
                    LOG.info("dbVal[" + i + "] : " + dbVal);
                    assertEquals(updatedParams[i][1], dbVal);
                    assertEquals(STEP_PRICE.multiply(stepLimit), stx.getUsedFee());
                }
                LOG.infoExiting();
            }
        }
        LOG.infoExiting();
    }
}
