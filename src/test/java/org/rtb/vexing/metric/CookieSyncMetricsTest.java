package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;
import org.rtb.vexing.metric.CookieSyncMetrics.BidderCookieSyncMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class CookieSyncMetricsTest {

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new CookieSyncMetrics(null, null));
        assertThatNullPointerException().isThrownBy(() -> new CookieSyncMetrics(new MetricRegistry(), null));

        assertThatNullPointerException().isThrownBy(
                () -> new BidderCookieSyncMetrics(null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new BidderCookieSyncMetrics(new MetricRegistry(), null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new BidderCookieSyncMetrics(new MetricRegistry(), CounterType.counter, null));
    }

    @Test
    public void forBidderShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new CookieSyncMetrics(new MetricRegistry(),
                CounterType.counter).forBidder(null));
    }

    @Test
    public void forBidderShouldReturnSameBidderCookieSyncMetricsOnSuccessiveCalls() {
        // given
        final CookieSyncMetrics cookieSyncMetrics = new CookieSyncMetrics(new MetricRegistry(), CounterType.counter);

        // when and then
        assertThat(cookieSyncMetrics.forBidder("rubicon")).isSameAs(cookieSyncMetrics.forBidder("rubicon"));
    }
}
