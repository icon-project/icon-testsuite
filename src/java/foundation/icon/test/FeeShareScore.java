package foundation.icon.test;

import foundation.icon.icx.IconService;
import foundation.icon.icx.Wallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.TransactionResult;
import foundation.icon.icx.transport.jsonrpc.RpcItem;
import foundation.icon.icx.transport.jsonrpc.RpcObject;
import foundation.icon.icx.transport.jsonrpc.RpcValue;
import foundation.icon.test.score.Score;

import java.io.IOException;
import java.math.BigInteger;

public class FeeShareScore extends Score {
    final static BigInteger Steps = BigInteger.valueOf(3).multiply(BigInteger.TEN.pow(6));

    private final Wallet wallet;

    public FeeShareScore(IconService service, Wallet wallet, Address target) {
        super(service, target);
        this.wallet = wallet;
    }

    public String getValue() throws IOException {
        RpcItem res = this.call("getValue", null);
        return res.asString();
    }

    public TransactionResult addToWhitelist(Address address, int proportion) throws IOException {
        return invokeAndWaitResult(wallet,
                "addToWhitelist",
                (new RpcObject.Builder())
                        .put("address", new RpcValue(address))
                        .put("proportion", new RpcValue(BigInteger.valueOf(proportion)))
                        .build(),
                null, Steps);
    }

    public TransactionResult setValue(String value) throws IOException {
        return invokeAndWaitResult(wallet,
                "setValue",
                (new RpcObject.Builder())
                        .put("value", new RpcValue(value))
                        .build(),
                null, Steps);
    }
}
