package org.prebid.server.auction.model;

import lombok.Value;
import org.prebid.server.execution.timeout.Timeout;

@Value(staticConstructor = "of")
public class TimeoutContext {

    long startTime;

    Timeout timeout;

    int adjustmentFactor;

    public TimeoutContext with(Timeout timeout) {
        return TimeoutContext.of(startTime, timeout, adjustmentFactor);
    }
}
