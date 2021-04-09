package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CookieSyncMetricsTest {

    @Test
    public void forBidderShouldReturnSameBidderCookieSyncMetricsOnSuccessiveCalls() {
        // given
        final CookieSyncMetrics cookieSyncMetrics = new CookieSyncMetrics(new MetricRegistry(), CounterType.counter);

        // when and then
        assertThat(cookieSyncMetrics.forBidder("rubicon")).isSameAs(cookieSyncMetrics.forBidder("rubicon"));
    }
}
