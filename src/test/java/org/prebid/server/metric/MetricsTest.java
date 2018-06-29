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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;

import java.util.EnumMap;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class MetricsTest {

    private static final String RUBICON = "rubicon";
    private static final String ACCOUNT_ID = "accountId";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private MetricRegistry metricRegistry;
    @Mock
    private AccountMetricsVerbosity accountMetricsVerbosity;

    private Metrics metrics;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.detailed);
        metrics = new Metrics(metricRegistry, CounterType.counter, accountMetricsVerbosity);
    }

    @Test
    public void createShouldReturnMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics.incCounter(MetricName.bids_received));
    }

    @Test
    public void forAccountShouldReturnSameAccountMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID)).isSameAs(metrics.forAccount(ACCOUNT_ID));
    }

    @Test
    public void forAccountShouldReturnAccountMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics.forAccount(ACCOUNT_ID).incCounter(MetricName.requests));
    }

    @Test
    public void forAccountShouldReturnAccountMetricsConfiguredWithAccount() {
        // when
        metrics.forAccount(ACCOUNT_ID).incCounter(MetricName.requests);

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
        assertThat(metrics.forAccount(ACCOUNT_ID).forAdapter(RUBICON))
                .isSameAs(metrics.forAccount(ACCOUNT_ID).forAdapter(RUBICON));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount(ACCOUNT_ID)
                .forAdapter(RUBICON)
                .incCounter(MetricName.bids_received));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount(ACCOUNT_ID).forAdapter(RUBICON).incCounter(MetricName.bids_received);

        // then
        assertThat(metricRegistry.counter("account.accountId.rubicon.bids_received").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnSameAccountAdapterRequestMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).forAdapter(RUBICON).request())
                .isSameAs(metrics.forAccount(ACCOUNT_ID).forAdapter(RUBICON).request());
    }

    @Test
    public void shouldReturnAccountAdapterRequestMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount(ACCOUNT_ID)
                .forAdapter(RUBICON)
                .request()
                .incCounter(MetricName.gotbids));
    }

    @Test
    public void shouldReturnAccountAdapterRequestMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount(ACCOUNT_ID).forAdapter(RUBICON).request().incCounter(MetricName.gotbids);

        // then
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.gotbids").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldReturnSameAccountRequestTypeMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).requestType())
                .isSameAs(metrics.forAccount(ACCOUNT_ID).requestType());
    }

    @Test
    public void shouldReturnAccountRequestTypeMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount(ACCOUNT_ID)
                .requestType()
                .incCounter(MetricName.openrtb2app));
    }

    @Test
    public void shouldReturnAccountRequestTypeMetricsConfiguredWithAccount() {
        // when
        metrics.forAccount(ACCOUNT_ID).requestType().incCounter(MetricName.openrtb2web);

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

    @Test
    public void updateSafariRequestsMetricShouldIncrementMetric() {
        // when
        metrics.updateSafariRequestsMetric(true);
        metrics.updateSafariRequestsMetric(false);

        // then
        assertThat(metricRegistry.counter("safari_requests").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAppAndNoCookieAndImpsRequestedMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(true, false, false, 1);
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(false, false, false, 2);
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(false, false, true, 1);
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(false, true, false, 1);

        // then
        assertThat(metricRegistry.counter("app_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("no_cookie_requests").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("safari_no_cookie_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("imps_requested").getCount()).isEqualTo(5);
    }

    @Test
    public void updateRequestTimeMetricShouldUpdateMetric() {
        // when
        metrics.updateRequestTimeMetric(456L);

        // then
        assertThat(metricRegistry.timer("request_time").getCount()).isEqualTo(1);
    }

    @Test
    public void updateRequestTypeMetricShouldIncrementMetric() {
        // when
        metrics.updateRequestTypeMetric(MetricName.openrtb2web, MetricName.ok);
        metrics.updateRequestTypeMetric(MetricName.openrtb2app, MetricName.err);
        metrics.updateRequestTypeMetric(MetricName.amp, MetricName.badinput);

        // then
        assertThat(metricRegistry.counter("requests.ok.openrtb2-web").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("requests.err.openrtb2-app").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("requests.badinput.amp").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAccountRequestMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAccountRequestMetrics(ACCOUNT_ID, MetricName.openrtb2web);

        // then
        assertThat(metricRegistry.counter("account.accountId.requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterRequestTypeAndNoCookieMetricsShouldUpdateMetrics() {
        // when
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(RUBICON, MetricName.openrtb2app, true);
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(RUBICON, MetricName.amp, false);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.openrtb2-app").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.amp").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.no_cookie_requests").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterResponseTimeShouldUpdateMetrics() {
        // when
        metrics.updateAdapterResponseTime(RUBICON, ACCOUNT_ID, 500);

        // then
        assertThat(metricRegistry.timer("adapter.rubicon.request_time").getCount()).isEqualTo(1);
        assertThat(metricRegistry.timer("account.accountId.rubicon.request_time").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterRequestNobidMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestNobidMetrics(RUBICON, ACCOUNT_ID);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.nobid").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.nobid").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterRequestGotbidsMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, ACCOUNT_ID);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.gotbids").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.gotbids").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterBidMetricsShouldUpdateMetrics() {
        // when
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, true, "banner");
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, false, "video");

        // then
        assertThat(metricRegistry.histogram("adapter.rubicon.prices").getCount()).isEqualTo(2);
        assertThat(metricRegistry.histogram("account.accountId.rubicon.prices").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.rubicon.bids_received").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("account.accountId.rubicon.bids_received").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.rubicon.banner.adm_bids_received").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.video.nurl_bids_received").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterRequestErrorMetricShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestErrorMetric(RUBICON, MetricName.badinput);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.badinput").getCount()).isEqualTo(1);
    }

    @Test
    public void updateCookieSyncRequestMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncRequestMetric();

        // then
        assertThat(metricRegistry.counter("cookie_sync_requests").getCount()).isEqualTo(1);
    }

    @Test
    public void updateCookieSyncOptoutMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncOptoutMetric();

        // then
        assertThat(metricRegistry.counter("usersync.opt_outs").getCount()).isEqualTo(1);
    }

    @Test
    public void updateCookieSyncBadRequestMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncBadRequestMetric();

        // then
        assertThat(metricRegistry.counter("usersync.bad_requests").getCount()).isEqualTo(1);
    }

    @Test
    public void updateCookieSyncSetsMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncSetsMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("usersync.rubicon.sets").getCount()).isEqualTo(1);
    }

    @Test
    public void updateCookieSyncGdprPreventMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncGdprPreventMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("usersync.rubicon.gdpr_prevent").getCount()).isEqualTo(1);
    }

    @Test
    public void updateGdprMaskedMetricShouldIncrementMetric() {
        // when
        metrics.updateGdprMaskedMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.gdpr_masked").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldNotUpdateAccountMetricsIfVerbosityIsNone() {
        // given
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.none);

        // when
        metrics.updateAccountRequestMetrics(ACCOUNT_ID, MetricName.openrtb2web);
        metrics.updateAdapterResponseTime(RUBICON, ACCOUNT_ID, 500);
        metrics.updateAdapterRequestNobidMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, true, "banner");

        // then
        assertThat(metricRegistry.counter("account.accountId.requests").getCount()).isEqualTo(0);
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isEqualTo(0);
        assertThat(metricRegistry.timer("account.accountId.rubicon.request_time").getCount()).isEqualTo(0);
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.nobid").getCount()).isEqualTo(0);
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.gotbids").getCount()).isEqualTo(0);
        assertThat(metricRegistry.histogram("account.accountId.rubicon.prices").getCount()).isEqualTo(0);
        assertThat(metricRegistry.counter("account.accountId.rubicon.bids_received").getCount()).isEqualTo(0);
    }

    @Test
    public void shouldUpdateAccountRequestsMetricOnlyIfVerbosityIsBasic() {
        // given
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.basic);

        // when
        metrics.updateAccountRequestMetrics(ACCOUNT_ID, MetricName.openrtb2web);
        metrics.updateAdapterResponseTime(RUBICON, ACCOUNT_ID, 500);
        metrics.updateAdapterRequestNobidMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, true, "banner");

        // then
        assertThat(metricRegistry.counter("account.accountId.requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isEqualTo(0);
        assertThat(metricRegistry.timer("account.accountId.rubicon.request_time").getCount()).isEqualTo(0);
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.nobid").getCount()).isEqualTo(0);
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.gotbids").getCount()).isEqualTo(0);
        assertThat(metricRegistry.histogram("account.accountId.rubicon.prices").getCount()).isEqualTo(0);
        assertThat(metricRegistry.counter("account.accountId.rubicon.bids_received").getCount()).isEqualTo(0);
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
            metricsConsumer.accept(
                    new Metrics(metricRegistry, CounterType.valueOf(counterType.name()), accountMetricsVerbosity));

            // then
            softly.assertThat(metricRegistry.getMetrics()).hasValueSatisfying(new Condition<>(
                    metric -> metric.getClass() == counterTypeClasses.get(counterType),
                    null));
        }

        softly.assertAll();
    }
}
