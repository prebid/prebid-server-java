package org.prebid.server.assertion;

import io.vertx.core.Future;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.ThrowableAssert;
import org.assertj.core.internal.ComparisonStrategy;
import org.assertj.core.internal.StandardComparisonStrategy;
import org.assertj.core.util.Preconditions;

public class FutureAssertion<VALUE> extends AbstractAssert<FutureAssertion<VALUE>, Future<VALUE>> {

    private ComparisonStrategy futureValueComparisonStrategy;

    private FutureAssertion(Future<VALUE> actual) {
        super(actual, FutureAssertion.class);
        this.futureValueComparisonStrategy = StandardComparisonStrategy.instance();
    }

    public static <VALUE> FutureAssertion<VALUE> assertThat(Future<VALUE> actual) {
        return new FutureAssertion<>(actual);
    }

    public FutureAssertion<VALUE> isSucceeded() {
        isNotNull();
        if (!actual.succeeded()) {
            failWithMessage("Expected future to be succeeded");
        }
        return myself;
    }

    public ThrowableAssert isFailed() {
        isNotNull();
        if (!actual.failed()) {
            failWithMessage("Expected future to be failed");
        }
        return new ThrowableAssert(actual.cause());
    }

    public FutureAssertion<VALUE> succeededWith(VALUE expectedValue) {
        isSucceeded();
        checkIsNotNull(expectedValue);

        final VALUE actualValue = actual.result();
        if (!futureValueComparisonStrategy.areEqual(actualValue, expectedValue)) {
            failWithMessage("Expected future to contain <%s> but was <%s>", expectedValue, actualValue);
        }

        return myself;
    }

    private <T> void checkIsNotNull(T expectedValue) {
        Preconditions.checkArgument(expectedValue != null, "The expected value should not be <null>.");
    }
}
