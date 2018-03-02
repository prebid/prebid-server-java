package org.prebid.server.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import org.assertj.core.api.Condition;
import org.assertj.core.api.SoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.EnumMap;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class MetricsTest {

    private static final String RUBICON = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private MetricRegistry metricRegistry;

    private Metrics metrics;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
        metrics = new Metrics(metricRegistry, CounterType.counter);
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new Metrics(null, null));
        assertThatNullPointerException().isThrownBy(() -> new Metrics(metricRegistry, null));
    }

    @Test
    public void createShouldReturnMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics.incCounter(MetricName.requests));
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
                metrics -> metrics.forAdapter(RUBICON).incCounter(MetricName.requests));
    }

    @Test
    public void forAdapterShouldReturnAdapterMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).incCounter(MetricName.requests);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount("accountId")
                .forAdapter(RUBICON)
                .incCounter(MetricName.requests));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount("accountId").forAdapter(RUBICON).incCounter(MetricName.requests);

        // then
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests").getCount()).isEqualTo(1);
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
