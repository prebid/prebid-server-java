package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.MockClock;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CookieSyncMetricsTest {

    @Test
    public void forBidderShouldReturnSameBidderCookieSyncMetricsOnSuccessiveCalls() {
        // given
        final MeterRegistry meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        final CookieSyncMetrics cookieSyncMetrics = new CookieSyncMetrics(meterRegistry);

        // when and then
        assertThat(cookieSyncMetrics.forBidder("rubicon")).isSameAs(cookieSyncMetrics.forBidder("rubicon"));
    }
}
