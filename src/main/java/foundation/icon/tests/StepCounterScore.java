package foundation.icon.tests;

import foundation.icon.icx.IconService;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;

import java.io.IOException;
import java.math.BigInteger;

public class StepCounterScore extends Score {
    final static BigInteger Steps = BigInteger.valueOf(3).multiply(BigInteger.TEN.pow(6));

    public static StepCounterScore mustDeploy(IconService service, Wallet wallet, String filePath)
            throws IOException, TransactionFailureException
    {
        return new StepCounterScore(
                service,
                Score.mustDeploy(service, wallet, filePath, null)
        );
    }

    StepCounterScore(IconService service, Address target) {
        super(service, target);
    }

    public TransactionResult increaseStep(Wallet wallet) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "increaseStep", null, null, Steps);
    }

    public TransactionResult setStep(Wallet wallet, BigInteger step) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "setStep",
                (new RpcObject.Builder())
                    .put("step", new RpcValue(step))
                    .build(),
                null, Steps);
    }

    public TransactionResult resetStep(Wallet wallet, BigInteger step) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "resetStep",
                (new RpcObject.Builder())
                        .put("step", new RpcValue(step))
                        .build(),
                null, Steps);
    }

    public TransactionResult setStepOf(Wallet wallet, Address target, BigInteger step) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "setStepOf",
                (new RpcObject.Builder())
                    .put("step", new RpcValue(step))
                    .put( "addr", new RpcValue(target))
                    .build(),
                null, Steps);
    }

    public TransactionResult trySetStepWith(Wallet wallet, Address target, BigInteger step) throws IOException {
        return this.invokeAndWaitResult(wallet,
                "trySetStepWith",
                (new RpcObject.Builder())
                        .put("step", new RpcValue(step))
                        .put( "addr", new RpcValue(target))
                        .build(),
                null, Steps);
    }

    public BigInteger getStep(Address from) throws IOException {
        RpcItem res = this.call("getStep", null);
        return res.asInteger();
    }
}