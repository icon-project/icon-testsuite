package foundation.icon.test;

import foundation.icon.icx.data.TransactionResult;

import java.math.BigInteger;

public class TransactionFailureException extends Exception {
    private static final BigInteger STATUS_SUCCESS = BigInteger.ONE;

    private TransactionResult.Failure failure;

    public TransactionFailureException(TransactionResult.Failure failure) {
        this.failure = failure;
    }

    @Override
    public String toString() {
        return this.failure.toString();
    }

    public BigInteger getCode() {
        return this.failure.getCode();
    }

    public String getMessage() {
        return this.failure.getMessage();
    }
}
