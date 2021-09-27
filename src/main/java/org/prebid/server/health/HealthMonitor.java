package org.prebid.server.health;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.LongAdder;

/**
 * Used to gather statistics and calculate the health index indicator.
 */
public class HealthMonitor {

    private final LongAdder totalCounter = new LongAdder();

    private final LongAdder successCounter = new LongAdder();

    /**
     * Increments total number of requests.
     */
    public void incTotal() {
        totalCounter.increment();
    }

    /**
     * Increments succeeded number of requests.
     */
    public void incSuccess() {
        successCounter.increment();
    }

    /**
     * Returns value between 0.0 ... 1.0 where 1.0 is indicated 100% healthy.
     */
    public BigDecimal calculateHealthIndex() {
        final BigDecimal success = BigDecimal.valueOf(successCounter.sumThenReset());
        final BigDecimal total = BigDecimal.valueOf(totalCounter.sumThenReset());
        return total.longValue() == 0 ? BigDecimal.ONE : success.divide(total, 2, RoundingMode.HALF_EVEN);
    }
}
