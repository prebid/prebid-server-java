package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.bidder.BidderCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class AccountMetricsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidderCatalog bidderCatalog;

    private AccountMetrics accountMetrics;

    @Before
    public void setUp() {
        accountMetrics = new AccountMetrics(new MetricRegistry(), CounterType.counter, bidderCatalog, "accountId");
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AccountMetrics(null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AccountMetrics(new MetricRegistry(), null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AccountMetrics(new MetricRegistry(), CounterType.counter,
                bidderCatalog, null));
        assertThatNullPointerException().isThrownBy(() -> new AccountMetrics(new MetricRegistry(), CounterType.counter,
                bidderCatalog, null));
    }

    @Test
    public void forAdapterShouldReturnSameAdapterMetricsOnSuccessiveCalls() {
        // given
        given(bidderCatalog.isActive(anyString())).willReturn(true);

        // when and then
        assertThat(accountMetrics.forAdapter("rubicon")).isSameAs(accountMetrics.forAdapter("rubicon"));
    }

    @Test
    public void forAdapterShouldReturnDisabledMetricsForDisabledBidder() {
        // given
        given(bidderCatalog.isActive("disabled")).willReturn(false);

        // when and then
        assertThat(accountMetrics.forAdapter("disabled")).isInstanceOf(DisabledAdapterMetrics.class);
    }
}
