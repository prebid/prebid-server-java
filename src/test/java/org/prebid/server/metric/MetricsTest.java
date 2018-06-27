package org.prebid.server.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import org.assertj.core.api.Condition;
import org.assertj.core.api.SoftAssertions;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumMap;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricsTest {

    private static final String RUBICON = "rubicon";

    private MetricRegistry metricRegistry;

    private Metrics metrics;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
        metrics = new Metrics(metricRegistry, CounterType.counter);
    }

    @Test
    public void createShouldReturnMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics.incCounter(MetricName.bids_received));
    }

    @Test
    public void forAccountShouldReturnSameAccountMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount("accountId")).isSameAs(metrics.forAccount("accountId"));
    }

    @Test
    public void forAccountShouldReturnAccountMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics.forAccount("accountId").incCounter(MetricName.requests));
    }

    @Test
    public void forAccountShouldReturnAccountMetricsConfiguredWithAccount() {
        // when
        metrics.forAccount("accountId").incCounter(MetricName.requests);

        // then
        assertThat(metricRegistry.counter("account.accountId.requests").getCount()).isEqualTo(1);
    }

    @Test
    public void forAdapterShouldReturnSameAdapterMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAdapter(RUBICON)).isSameAs(metrics.forAdapter(RUBICON));
    }

    @Test
    public void forAdapterShouldReturnAdapterMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(
                metrics -> metrics.forAdapter(RUBICON).incCounter(MetricName.bids_received));
    }

    @Test
    public void forAdapterShouldReturnAdapterMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).incCounter(MetricName.bids_received);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.bids_received").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnSameAdapterRequestTypeMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAdapter(RUBICON).requestType())
                .isSameAs(metrics.forAdapter(RUBICON).requestType());
    }

    @Test
    public void shouldReturnAdapterRequestTypeMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAdapter(RUBICON)
                .requestType()
                .incCounter(MetricName.openrtb2app));
    }

    @Test
    public void shouldReturnAdapterRequestTypeMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).requestType().incCounter(MetricName.openrtb2web);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.openrtb2-web").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnSameAdapterRequestMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAdapter(RUBICON).request())
                .isSameAs(metrics.forAdapter(RUBICON).request());
    }

    @Test
    public void shouldReturnAdapterRequestMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAdapter(RUBICON)
                .request()
                .incCounter(MetricName.gotbids));
    }

    @Test
    public void shouldReturnAdapterRequestMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).request().incCounter(MetricName.gotbids);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.gotbids").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnSameAccountAdapterMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount("accountId").forAdapter(RUBICON))
                .isSameAs(metrics.forAccount("accountId").forAdapter(RUBICON));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount("accountId")
                .forAdapter(RUBICON)
                .incCounter(MetricName.bids_received));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount("accountId").forAdapter(RUBICON).incCounter(MetricName.bids_received);

        // then
        assertThat(metricRegistry.counter("account.accountId.rubicon.bids_received").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnSameAccountAdapterRequestMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount("accountId").forAdapter(RUBICON).request())
                .isSameAs(metrics.forAccount("accountId").forAdapter(RUBICON).request());
    }

    @Test
    public void shouldReturnAccountAdapterRequestMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount("accountId")
                .forAdapter(RUBICON)
                .request()
                .incCounter(MetricName.gotbids));
    }

    @Test
    public void shouldReturnAccountAdapterRequestMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount("accountId").forAdapter(RUBICON).request().incCounter(MetricName.gotbids);

        // then
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.gotbids").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnSameAccountRequestTypeMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount("accountId").requestType())
                .isSameAs(metrics.forAccount("accountId").requestType());
    }

    @Test
    public void shouldReturnAccountRequestTypeMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount("accountId")
                .requestType()
                .incCounter(MetricName.openrtb2app));
    }

    @Test
    public void shouldReturnAccountRequestTypeMetricsConfiguredWithAccount() {
        // when
        metrics.forAccount("accountId").requestType().incCounter(MetricName.openrtb2web);

        // then
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isEqualTo(1);
    }

    @Test
    public void cookieSyncShouldReturnSameCookieSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.cookieSync()).isSameAs(metrics.cookieSync());
    }

    @Test
    public void cookieSyncShouldReturnCookieSyncMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(
                metrics -> metrics.cookieSync().incCounter(MetricName.opt_outs));
    }

    @Test
    public void cookieSyncShouldReturnCookieSyncMetricsConfiguredWithPrefix() {
        // when
        metrics.cookieSync().incCounter(MetricName.opt_outs);

        // then
        assertThat(metricRegistry.counter("usersync.opt_outs").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnSameBidderCookieSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.cookieSync().forBidder(RUBICON)).isSameAs(metrics.cookieSync().forBidder(RUBICON));
    }

    @Test
    public void shouldReturnBidderCookieSyncMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .cookieSync()
                .forBidder(RUBICON)
                .incCounter(MetricName.sets));
    }

    @Test
    public void shouldReturnBidderCookieSyncMetricsConfiguredWithBidder() {
        // when
        metrics.cookieSync().forBidder(RUBICON).incCounter(MetricName.sets);

        // then
        assertThat(metricRegistry.counter("usersync.rubicon.sets").getCount()).isEqualTo(1);
    }

    @Test
    public void forRequestTypeShouldReturnSameRequestStatusMetricsOnSuccessiveCalls() {
        assertThat(metrics.forRequestType(MetricName.openrtb2web))
                .isSameAs(metrics.forRequestType(MetricName.openrtb2web));
    }

    @Test
    public void forRequestTypeShouldReturnRequestStatusMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forRequestType(MetricName.openrtb2web)
                .incCounter(MetricName.ok));
    }

    @Test
    public void forRequestTypeShouldReturnRequestStatusMetricsConfiguredWithRequestType() {
        // when
        metrics.forRequestType(MetricName.openrtb2web).incCounter(MetricName.ok);

        // then
        assertThat(metricRegistry.counter("requests.ok.openrtb2-web").getCount()).isEqualTo(1);
    }

    private void verifyCreatesConfiguredCounterType(Consumer<Metrics> metricsConsumer) {
        final EnumMap<CounterType, Class<? extends Metric>> counterTypeClasses = new EnumMap<>(CounterType.class);
        counterTypeClasses.put(CounterType.counter, Counter.class);
        counterTypeClasses.put(CounterType.flushingCounter, ResettingCounter.class);
        counterTypeClasses.put(CounterType.meter, Meter.class);

        final SoftAssertions softly = new SoftAssertions();

        for (CounterType counterType : CounterType.values()) {
            // given

            metricRegistry = new MetricRegistry();

            // when
            metricsConsumer.accept(new Metrics(metricRegistry, CounterType.valueOf(counterType.name())));

            // then
            softly.assertThat(metricRegistry.getMetrics()).hasValueSatisfying(new Condition<>(
                    metric -> metric.getClass() == counterTypeClasses.get(counterType),
                    null));
        }

        softly.assertAll();
    }
}
