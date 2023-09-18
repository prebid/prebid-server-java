package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserSyncMetricsTest {

    @Test
    public void forBidderShouldReturnSameBidderCookieSyncMetricsOnSuccessiveCalls() {
        // given
        final UserSyncMetrics userSyncMetrics = new UserSyncMetrics(new MetricRegistry(), CounterType.counter);

        // when and then
        assertThat(userSyncMetrics.forBidder("rubicon")).isSameAs(userSyncMetrics.forBidder("rubicon"));
    }
}
