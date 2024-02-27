package org.prebid.server.spring.config.retry;

import lombok.Data;
import org.prebid.server.execution.retry.ExponentialBackoffRetryPolicy;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;

@Data
@Validated
public final class ExponentialBackoffRetryPolicyConfigurationProperties {

    @Min(1)
    long delayMillis;

    @Min(1)
    long maxDelayMillis;

    @Positive
    double factor;

    @Positive
    double jitter;

    public ExponentialBackoffRetryPolicy toPolicy() {
        return ExponentialBackoffRetryPolicy.of(delayMillis, maxDelayMillis, factor, jitter);
    }
}

