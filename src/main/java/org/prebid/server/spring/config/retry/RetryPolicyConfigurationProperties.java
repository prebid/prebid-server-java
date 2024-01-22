package org.prebid.server.spring.config.retry;

import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.execution.retry.RetryPolicy;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class RetryPolicyConfigurationProperties {

    private ExponentialBackoffRetryPolicyConfigurationProperties exponentialBackoff;

    private FixedIntervalRetryPolicyConfigurationProperties fixedInterval;

    public RetryPolicy toPolicy() {
        if (ObjectUtils.allNull(exponentialBackoff, fixedInterval)) {
            throw new IllegalArgumentException("Invalid configuration of retry policy. No retry policy specified.");
        }
        if (ObjectUtils.allNotNull(exponentialBackoff, fixedInterval)) {
            throw new IllegalArgumentException("Invalid configuration of retry policy." +
                    " Should be either exponential backoff or fixed interval, but not both.");
        }

        return exponentialBackoff != null ? exponentialBackoff.toPolicy() : fixedInterval.toPolicy();
    }
}
