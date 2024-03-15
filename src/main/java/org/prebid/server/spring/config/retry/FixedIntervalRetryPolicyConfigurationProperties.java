package org.prebid.server.spring.config.retry;

import lombok.Data;
import org.prebid.server.execution.retry.FixedIntervalRetryPolicy;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

@Data
@Validated
public final class FixedIntervalRetryPolicyConfigurationProperties {

    @Min(1)
    long delay;

    @Min(0)
    Integer retriesLeft;

    FixedIntervalRetryPolicy toPolicy() {
        return retriesLeft == null
                ? FixedIntervalRetryPolicy.of(delay)
                : FixedIntervalRetryPolicy.limited(delay, retriesLeft);
    }
}
