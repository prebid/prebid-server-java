package org.prebid.server.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import org.assertj.core.api.Condition;
import org.assertj.core.api.SoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.Activity;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;
import org.prebid.server.settings.model.Account;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class MetricsTest {

    private static final String RUBICON = "rubicon";
    private static final String CONVERSANT = "conversant";
    private static final String ACCOUNT_ID = "accountId";
    private static final String ANALYTIC_CODE = "analyticCode";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private MetricRegistry metricRegistry;
    @Mock
    private AccountMetricsVerbosityResolver accountMetricsVerbosityResolver;

    private Metrics metrics;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
        given(accountMetricsVerbosityResolver.forAccount(any())).willReturn(AccountMetricsVerbosityLevel.detailed);

        metrics = new Metrics(metricRegistry, CounterType.counter, accountMetricsVerbosityResolver);
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
        assertThat(metricRegistry.counter("account.accountId.requests").getCount()).isOne();
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
        assertThat(metricRegistry.counter("adapter.rubicon.bids_received").getCount()).isOne();
    }

    @Test
    public void shouldReturnSameAdapterRequestTypeMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAdapter(RUBICON).requestType(MetricName.amp))
                .isSameAs(metrics.forAdapter(RUBICON).requestType(MetricName.amp));
    }

    @Test
    public void shouldReturnAdapterRequestTypeMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAdapter(RUBICON)
                .requestType(MetricName.openrtb2app)
                .incCounter(MetricName.requests));
    }

    @Test
    public void shouldReturnAdapterRequestTypeMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).requestType(MetricName.openrtb2web).incCounter(MetricName.requests);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.openrtb2-web").getCount()).isOne();
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
        assertThat(metricRegistry.counter("adapter.rubicon.requests.gotbids").getCount()).isOne();
    }

    @Test
    public void shouldReturnSameAccountAdapterMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter("rUbIcOn"))
                .isSameAs(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount(ACCOUNT_ID)
                .adapter()
                .forAdapter(RUBICON)
                .incCounter(MetricName.bids_received));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).incCounter(MetricName.bids_received);

        // then
        assertThat(metricRegistry.counter("account.accountId.adapter.rubicon.bids_received").getCount()).isOne();
    }

    @Test
    public void shouldReturnSameAccountAdapterRequestMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).request())
                .isSameAs(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).request());
    }

    @Test
    public void shouldReturnAccountAdapterRequestMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount(ACCOUNT_ID)
                .adapter()
                .forAdapter(RUBICON)
                .request()
                .incCounter(MetricName.gotbids));
    }

    @Test
    public void shouldReturnAccountAdapterRequestMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).request().incCounter(MetricName.gotbids);

        // then
        assertThat(metricRegistry.counter("account.accountId.adapter.rubicon.requests.gotbids").getCount())
                .isOne();
    }

    @Test
    public void shouldReturnSameAccountRequestTypeMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).requestType(MetricName.amp))
                .isSameAs(metrics.forAccount(ACCOUNT_ID).requestType(MetricName.amp));
    }

    @Test
    public void shouldReturnAccountRequestTypeMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount(ACCOUNT_ID)
                .requestType(MetricName.openrtb2app)
                .incCounter(MetricName.requests));
    }

    @Test
    public void shouldReturnAccountRequestTypeMetricsConfiguredWithAccount() {
        // when
        metrics.forAccount(ACCOUNT_ID).requestType(MetricName.openrtb2web).incCounter(MetricName.requests);

        // then
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isOne();
    }

    @Test
    public void userSyncShouldReturnSameUserSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.userSync()).isSameAs(metrics.userSync());
    }

    @Test
    public void userSyncShouldReturnUserSyncMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(
                metrics -> metrics.userSync().incCounter(MetricName.opt_outs));
    }

    @Test
    public void userSyncShouldReturnUserSyncMetricsConfiguredWithPrefix() {
        // when
        metrics.userSync().incCounter(MetricName.opt_outs);

        // then
        assertThat(metricRegistry.counter("usersync.opt_outs").getCount()).isOne();
    }

    @Test
    public void shouldReturnSameBidderUserSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.userSync().forBidder(RUBICON)).isSameAs(metrics.userSync().forBidder(RUBICON));
    }

    @Test
    public void shouldReturnBidderUserSyncMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .userSync()
                .forBidder(RUBICON)
                .incCounter(MetricName.sets));
    }

    @Test
    public void shouldReturnBidderUserSyncMetricsConfiguredWithBidder() {
        // when
        metrics.userSync().forBidder(RUBICON).incCounter(MetricName.sets);

        // then
        assertThat(metricRegistry.counter("usersync.rubicon.sets").getCount()).isOne();
    }

    @Test
    public void cookieSyncShouldReturnSameCookieSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.cookieSync()).isSameAs(metrics.cookieSync());
    }

    @Test
    public void shouldReturnSameBidderCookieSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.cookieSync().forBidder(RUBICON)).isSameAs(metrics.cookieSync().forBidder(RUBICON));
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
        assertThat(metricRegistry.counter("requests.ok.openrtb2-web").getCount()).isOne();
    }

    @Test
    public void forCircuitBreakerShouldReturnSameCircuitBreakerMetricsOnSuccessiveCalls() {
        assertThat(metrics.forCircuitBreakerType(MetricName.db)).isSameAs(metrics.forCircuitBreakerType(MetricName.db));
    }

    @Test
    public void forCircuitBreakerShouldReturnCircuitBreakerMetricsConfiguredWithId() {
        // when
        metrics.forCircuitBreakerType(MetricName.db).createGauge(MetricName.opened, () -> 1);

        // then
        assertThat(metricRegistry.gauge("circuit-breaker.db.opened.count", () -> null).getValue()).isEqualTo(1L);
    }

    @Test
    public void updateAppAndNoCookieAndImpsRequestedMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(true, false, 1);
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(false, false, 2);
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(false, true, 1);

        // then
        assertThat(metricRegistry.counter("app_requests").getCount()).isOne();
        assertThat(metricRegistry.counter("no_cookie_requests").getCount()).isOne();
        assertThat(metricRegistry.counter("imps_requested").getCount()).isEqualTo(4);
    }

    @Test
    public void updateImpTypesMetricsByCountPerMediaTypeShouldIncrementMetrics() {
        // given
        final Map<String, Long> mediaTypeToCount = new HashMap<>();
        mediaTypeToCount.put("banner", 3L);
        mediaTypeToCount.put("video", 5L);
        mediaTypeToCount.put("native", 1L);
        mediaTypeToCount.put("audio", 4L);
        mediaTypeToCount.put("bad_mediatype", 11L);

        // when
        metrics.updateImpTypesMetrics(mediaTypeToCount);

        // then
        assertThat(metricRegistry.counter("imps_banner").getCount()).isEqualTo(3);
        assertThat(metricRegistry.counter("imps_video").getCount()).isEqualTo(5);
        assertThat(metricRegistry.counter("imps_native").getCount()).isOne();
        assertThat(metricRegistry.counter("imps_audio").getCount()).isEqualTo(4);
    }

    @Test
    public void updateImpTypesMetricsByImpsShouldGroupCountByMediaTypeAndCallOverloadedMethodToIncrementMetrics() {
        // given
        final Metrics metricsSpy = Mockito.spy(metrics);

        final List<Imp> imps = asList(
                Imp.builder().banner(Banner.builder().build()).video(Video.builder().build()).build(),
                Imp.builder().xNative(Native.builder().build()).build(),
                Imp.builder().audio(Audio.builder().build()).build(),
                Imp.builder().video(Video.builder().build()).audio(Audio.builder().build()).build());

        // when
        metricsSpy.updateImpTypesMetrics(imps);

        // then
        final Map<String, Long> expectedMap = new HashMap<>();
        expectedMap.put("banner", 1L);
        expectedMap.put("video", 2L);
        expectedMap.put("native", 1L);
        expectedMap.put("audio", 2L);

        verify(metricsSpy).updateImpTypesMetrics(eq(expectedMap));

        assertThat(metricRegistry.counter("imps_banner").getCount()).isOne();
        assertThat(metricRegistry.counter("imps_video").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("imps_native").getCount()).isOne();
        assertThat(metricRegistry.counter("imps_audio").getCount()).isEqualTo(2);
    }

    @Test
    public void updateRequestTimeMetricShouldUpdateMetric() {
        // when
        metrics.updateRequestTimeMetric(MetricName.request_time, 456L);

        // then
        assertThat(metricRegistry.timer("request_time").getCount()).isOne();
    }

    @Test
    public void updateRequestTypeMetricShouldIncrementMetric() {
        // when
        metrics.updateRequestTypeMetric(MetricName.openrtb2web, MetricName.ok);
        metrics.updateRequestTypeMetric(MetricName.openrtb2web, MetricName.blacklisted_account);
        metrics.updateRequestTypeMetric(MetricName.openrtb2app, MetricName.blacklisted_app);
        metrics.updateRequestTypeMetric(MetricName.openrtb2app, MetricName.err);
        metrics.updateRequestTypeMetric(MetricName.amp, MetricName.badinput);
        metrics.updateRequestTypeMetric(MetricName.amp, MetricName.networkerr);

        // then
        assertThat(metricRegistry.counter("requests.ok.openrtb2-web").getCount()).isOne();
        assertThat(metricRegistry.counter("requests.blacklisted_account.openrtb2-web").getCount()).isOne();
        assertThat(metricRegistry.counter("requests.blacklisted_app.openrtb2-app").getCount()).isOne();
        assertThat(metricRegistry.counter("requests.err.openrtb2-app").getCount()).isOne();
        assertThat(metricRegistry.counter("requests.badinput.amp").getCount()).isOne();
        assertThat(metricRegistry.counter("requests.networkerr.amp").getCount()).isOne();
    }

    @Test
    public void uupdateRequestBidderCardinalityMetricShouldIncrementMetrics() {
        // when
        metrics.updateRequestBidderCardinalityMetric(3);

        // then
        assertThat(metricRegistry.counter("bidder-cardinality.3.requests").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAccountRequestMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAccountRequestMetrics(Account.empty(ACCOUNT_ID), MetricName.openrtb2web);

        // then
        assertThat(metricRegistry.counter("account.accountId.requests").getCount()).isOne();
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isOne();
    }

    @Test
    public void updateAdapterRequestTypeAndNoCookieMetricsShouldUpdateMetricsAsExpected() {

        // when
        metrics.updateAdapterRequestTypeAndNoCookieMetrics("rUbIcON", MetricName.openrtb2app, true);
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(RUBICON, MetricName.amp, false);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.openrtb2-app").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.no_cookie_requests").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.amp").getCount()).isOne();
    }

    @Test
    public void updateAnalyticWithEventTypeShouldUpdateMetricsAsExpected() {

        // when
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.event_auction, MetricName.ok);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.event_amp, MetricName.timeout);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.event_video, MetricName.err);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.event_cookie_sync, MetricName.timeout);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.event_notification, MetricName.err);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.event_setuid, MetricName.badinput);

        // then
        assertThat(metricRegistry.counter("analytics.analyticCode.auction.ok").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.amp.timeout").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.video.err").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.cookie_sync.timeout").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.event.err").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.setuid.badinput").getCount()).isOne();
    }

    @Test
    public void updateFetchWithFetchResultShouldCreateMetricsAsExpected() {
        // when
        metrics.updatePriceFloorFetchMetric(MetricName.failure);

        // then
        assertThat(metricRegistry.counter("price-floors.fetch.failure").getCount()).isOne();
    }

    @Test
    public void updatePriceFloorGeneralErrorsShouldCreateMetricsAsExpected() {
        // when
        metrics.updatePriceFloorGeneralAlertsMetric(MetricName.err);

        // then
        assertThat(metricRegistry.counter("price-floors.general.err").getCount()).isOne();
    }

    @Test
    public void updateAlertsMetricsShouldCreateMetricsAsExpected() {
        // when
        metrics.updateAlertsMetrics(MetricName.general);
        metrics.updateAlertsMetrics(MetricName.failed);
        metrics.updateAlertsMetrics(MetricName.general);

        // then
        assertThat(metricRegistry.counter("alerts.general").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("alerts.failed").getCount()).isOne();
    }

    @Test
    public void updateAlertsConfigMetricsShouldCreateMetricsAsExpected() {
        // when
        metrics.updateAlertsConfigFailed("accountId", MetricName.price_floors);
        metrics.updateAlertsConfigFailed("anotherId", MetricName.failed);
        metrics.updateAlertsConfigFailed("accountId", MetricName.price_floors);

        // then
        assertThat(metricRegistry.counter("alerts.account_config.accountId.price-floors")
                .getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("alerts.account_config.anotherId.failed")
                .getCount()).isOne();
    }

    @Test
    public void updateAdapterResponseTimeShouldUpdateMetrics() {
        // when
        metrics.updateAdapterResponseTime(RUBICON, Account.empty(ACCOUNT_ID), 500);
        metrics.updateAdapterResponseTime(CONVERSANT, Account.empty(ACCOUNT_ID), 500);
        metrics.updateAdapterResponseTime(CONVERSANT, Account.empty(ACCOUNT_ID), 500);

        // then
        assertThat(metricRegistry.timer("adapter.rubicon.request_time").getCount()).isOne();
        assertThat(metricRegistry.timer("account.accountId.adapter.rubicon.request_time").getCount()).isOne();
        assertThat(metricRegistry.timer("adapter.conversant.request_time").getCount()).isEqualTo(2);
        assertThat(metricRegistry.timer("account.accountId.adapter.conversant.request_time").getCount()).isEqualTo(2);
    }

    @Test
    public void updateAdapterRequestNobidMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestNobidMetrics(RUBICON, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestNobidMetrics(CONVERSANT, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestNobidMetrics(CONVERSANT, Account.empty(ACCOUNT_ID));

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.nobid").getCount()).isOne();
        assertThat(metricRegistry.counter("account.accountId.adapter.rubicon.requests.nobid").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.requests.nobid").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("account.accountId.adapter.conversant.requests.nobid").getCount())
                .isEqualTo(2);
    }

    @Test
    public void updateAdapterRequestGotbidsMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestGotbidsMetrics(CONVERSANT, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestGotbidsMetrics(CONVERSANT, Account.empty(ACCOUNT_ID));

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.gotbids").getCount()).isOne();
        assertThat(metricRegistry.counter("account.accountId.adapter.rubicon.requests.gotbids").getCount())
                .isOne();
        assertThat(metricRegistry.counter("adapter.conversant.requests.gotbids").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("account.accountId.adapter.conversant.requests.gotbids").getCount())
                .isEqualTo(2);
    }

    @Test
    public void updateAdapterBidMetricsShouldUpdateMetrics() {
        // when
        metrics.updateAdapterBidMetrics(RUBICON, Account.empty(ACCOUNT_ID), 1234L, true, "banner");
        metrics.updateAdapterBidMetrics(RUBICON, Account.empty(ACCOUNT_ID), 1234L, false, "video");
        metrics.updateAdapterBidMetrics(CONVERSANT, Account.empty(ACCOUNT_ID), 1234L, false, "banner");
        metrics.updateAdapterBidMetrics(CONVERSANT, Account.empty(ACCOUNT_ID), 1234L, false, "banner");

        // then
        assertThat(metricRegistry.histogram("adapter.rubicon.prices").getCount()).isEqualTo(2);
        assertThat(metricRegistry.histogram("account.accountId.adapter.rubicon.prices").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.rubicon.bids_received").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("account.accountId.adapter.rubicon.bids_received").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.rubicon.banner.adm_bids_received").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.video.nurl_bids_received").getCount()).isOne();
        assertThat(metricRegistry.histogram("adapter.conversant.prices").getCount()).isEqualTo(2);
        assertThat(metricRegistry.histogram("account.accountId.adapter.conversant.prices").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.conversant.bids_received").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("account.accountId.adapter.conversant.bids_received").getCount())
                .isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.conversant.banner.nurl_bids_received").getCount()).isEqualTo(2);
    }

    @Test
    public void updateAdapterRequestErrorMetricShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestErrorMetric(RUBICON, MetricName.badinput);
        metrics.updateAdapterRequestErrorMetric(CONVERSANT, MetricName.badinput);
        metrics.updateAdapterRequestErrorMetric(CONVERSANT, MetricName.badinput);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.badinput").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.requests.badinput").getCount()).isEqualTo(2);
    }

    @Test
    public void updateSizeValidationMetricsShouldIncrementMetrics() {
        // when
        metrics.updateSizeValidationMetrics(RUBICON, ACCOUNT_ID, MetricName.err);
        metrics.updateSizeValidationMetrics(CONVERSANT, ACCOUNT_ID, MetricName.err);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.response.validation.size.err").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.conversant.response.validation.size.err").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.response.validation.size.err").getCount()).isEqualTo(2);
    }

    @Test
    public void updateSecureValidationMetricsShouldIncrementMetrics() {
        // when
        metrics.updateSecureValidationMetrics(RUBICON, ACCOUNT_ID, MetricName.err);
        metrics.updateSecureValidationMetrics(CONVERSANT, ACCOUNT_ID, MetricName.err);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.response.validation.secure.err").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.conversant.response.validation.secure.err").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.response.validation.secure.err").getCount()).isEqualTo(2);
    }

    @Test
    public void updateCookieSyncRequestMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncRequestMetric();

        // then
        assertThat(metricRegistry.counter("cookie_sync_requests").getCount()).isOne();
    }

    @Test
    public void updateUserSyncOptoutMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncOptoutMetric();

        // then
        assertThat(metricRegistry.counter("usersync.opt_outs").getCount()).isOne();
    }

    @Test
    public void updateUserSyncBadRequestMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncBadRequestMetric();

        // then
        assertThat(metricRegistry.counter("usersync.bad_requests").getCount()).isOne();
    }

    @Test
    public void updateUserSyncSetsMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncSetsMetric("RUBICON");

        // then
        assertThat(metricRegistry.counter("usersync.rubicon.sets").getCount()).isOne();
    }

    @Test
    public void updateUserSyncTcfBlockedMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncTcfBlockedMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("usersync.rubicon.tcf.blocked").getCount()).isOne();
    }

    @Test
    public void updateCookieSyncTcfBlockedMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncTcfBlockedMetric(RUBICON);
        metrics.updateCookieSyncTcfBlockedMetric(CONVERSANT);
        metrics.updateCookieSyncTcfBlockedMetric(CONVERSANT);

        // then
        assertThat(metricRegistry.counter("cookie_sync.rubicon.tcf.blocked").getCount()).isOne();
        assertThat(metricRegistry.counter("cookie_sync.conversant.tcf.blocked").getCount()).isEqualTo(2);
    }

    @Test
    public void updateCookieSyncFilteredMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncFilteredMetric(RUBICON);
        metrics.updateCookieSyncFilteredMetric("CONVERSANT");
        metrics.updateCookieSyncFilteredMetric(CONVERSANT);

        // then
        assertThat(metricRegistry.counter("cookie_sync.rubicon.filtered").getCount()).isOne();
        assertThat(metricRegistry.counter("cookie_sync.conversant.filtered").getCount()).isEqualTo(2);
    }

    @Test
    public void updateAuctionTcfMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAuctionTcfMetrics(RUBICON, MetricName.openrtb2web, true, true, true, true, true);
        metrics.updateAuctionTcfMetrics(CONVERSANT, MetricName.openrtb2web, true, false, true, false, true);
        metrics.updateAuctionTcfMetrics(CONVERSANT, MetricName.openrtb2app, false, true, false, true, false);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.userfpd_masked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.userid_removed").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.geo_masked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.analytics_blocked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.request_blocked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.openrtb2-web.tcf.userfpd_masked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.openrtb2-app.tcf.userid_removed").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.openrtb2-web.tcf.geo_masked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.openrtb2-app.tcf.analytics_blocked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.openrtb2-web.tcf.request_blocked").getCount()).isOne();
    }

    @Test
    public void privacyShouldReturnSameMetricsOnSuccessiveCalls() {
        assertThat(metrics.privacy()).isSameAs(metrics.privacy());
    }

    @Test
    public void privacyTcfShouldReturnSameMetricsOnSuccessiveCalls() {
        assertThat(metrics.privacy().tcf()).isSameAs(metrics.privacy().tcf());
    }

    @Test
    public void privacyTcfVersionShouldReturnSameMetricsOnSuccessiveCalls() {
        assertThat(metrics.privacy().tcf().fromVersion(1)).isSameAs(metrics.privacy().tcf().fromVersion(1));
    }

    @Test
    public void privacyTcfVersionVendorListShouldReturnSameMetricsOnSuccessiveCalls() {
        assertThat(metrics.privacy().tcf().fromVersion(2).vendorList())
                .isSameAs(metrics.privacy().tcf().fromVersion(2).vendorList());
    }

    @Test
    public void updatePrivacyCoppaMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyCoppaMetric();

        // then
        assertThat(metricRegistry.counter("privacy.coppa").getCount()).isOne();
    }

    @Test
    public void updatePrivacyLmtMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyLmtMetric();

        // then
        assertThat(metricRegistry.counter("privacy.lmt").getCount()).isOne();
    }

    @Test
    public void updatePrivacyCcpaMetricsShouldIncrementMetrics() {
        // when
        metrics.updatePrivacyCcpaMetrics(true, true);

        // then
        assertThat(metricRegistry.counter("privacy.usp.specified").getCount()).isOne();
        assertThat(metricRegistry.counter("privacy.usp.opt-out").getCount()).isOne();
    }

    @Test
    public void updatePrivacyTcfMissingMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfMissingMetric();

        // then
        assertThat(metricRegistry.counter("privacy.tcf.missing").getCount()).isOne();
    }

    @Test
    public void updatePrivacyTcfInvalidMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfInvalidMetric();

        // then
        assertThat(metricRegistry.counter("privacy.tcf.invalid").getCount()).isOne();
    }

    @Test
    public void updatePrivacyTcfRequestsMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfRequestsMetric(1);

        // then
        assertThat(metricRegistry.counter("privacy.tcf.v1.requests").getCount()).isOne();
    }

    @Test
    public void updatePrivacyTcfGeoMetricShouldIncrementMetrics() {
        // when
        metrics.updatePrivacyTcfGeoMetric(1, null);
        metrics.updatePrivacyTcfGeoMetric(2, true);
        metrics.updatePrivacyTcfGeoMetric(2, false);

        // then
        assertThat(metricRegistry.counter("privacy.tcf.v1.unknown-geo").getCount()).isOne();
        assertThat(metricRegistry.counter("privacy.tcf.v2.in-geo").getCount()).isOne();
        assertThat(metricRegistry.counter("privacy.tcf.v2.out-geo").getCount()).isOne();
    }

    @Test
    public void updatePrivacyTcfVendorListMissingMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfVendorListMissingMetric(1);

        // then
        assertThat(metricRegistry.counter("privacy.tcf.v1.vendorlist.missing").getCount()).isOne();
    }

    @Test
    public void updatePrivacyTcfVendorListOkMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfVendorListOkMetric(1);

        // then
        assertThat(metricRegistry.counter("privacy.tcf.v1.vendorlist.ok").getCount()).isOne();
    }

    @Test
    public void updatePrivacyTcfVendorListErrorMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfVendorListErrorMetric(1);

        // then
        assertThat(metricRegistry.counter("privacy.tcf.v1.vendorlist.err").getCount()).isOne();
    }

    @Test
    public void updatePrivacyTcfVendorListFallbackMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfVendorListFallbackMetric(1);

        // then
        assertThat(metricRegistry.counter("privacy.tcf.v1.vendorlist.fallback").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldNotUpdateAccountMetricsIfVerbosityIsNone() {
        // given
        given(accountMetricsVerbosityResolver.forAccount(any())).willReturn(AccountMetricsVerbosityLevel.none);

        // when
        metrics.updateAccountRequestMetrics(Account.empty(ACCOUNT_ID), MetricName.openrtb2web);
        metrics.updateAdapterResponseTime(RUBICON, Account.empty(ACCOUNT_ID), 500);
        metrics.updateAdapterRequestNobidMetrics(RUBICON, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterBidMetrics(RUBICON, Account.empty(ACCOUNT_ID), 1234L, true, "banner");

        // then
        assertThat(metricRegistry.counter("account.accountId.requests").getCount()).isZero();
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isZero();
        assertThat(metricRegistry.timer("account.accountId.rubicon.request_time").getCount()).isZero();
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.nobid").getCount()).isZero();
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.gotbids").getCount()).isZero();
        assertThat(metricRegistry.histogram("account.accountId.rubicon.prices").getCount()).isZero();
        assertThat(metricRegistry.counter("account.accountId.rubicon.bids_received").getCount()).isZero();
    }

    @Test
    public void shouldUpdateAccountRequestsMetricOnlyIfVerbosityIsBasic() {
        // given
        given(accountMetricsVerbosityResolver.forAccount(any())).willReturn(AccountMetricsVerbosityLevel.basic);

        // when
        metrics.updateAccountRequestMetrics(Account.empty(ACCOUNT_ID), MetricName.openrtb2web);
        metrics.updateAdapterResponseTime(RUBICON, Account.empty(ACCOUNT_ID), 500);
        metrics.updateAdapterRequestNobidMetrics(RUBICON, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterBidMetrics(RUBICON, Account.empty(ACCOUNT_ID), 1234L, true, "banner");

        // then
        assertThat(metricRegistry.counter("account.accountId.requests").getCount()).isOne();
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isZero();
        assertThat(metricRegistry.timer("account.accountId.rubicon.request_time").getCount()).isZero();
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.nobid").getCount()).isZero();
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.gotbids").getCount()).isZero();
        assertThat(metricRegistry.histogram("account.accountId.rubicon.prices").getCount()).isZero();
        assertThat(metricRegistry.counter("account.accountId.rubicon.bids_received").getCount()).isZero();
    }

    @Test
    public void shouldIncrementConnectionAcceptErrorsMetric() {
        // when
        metrics.updateConnectionAcceptErrors();

        // then
        assertThat(metricRegistry.counter("connection_accept_errors").getCount()).isOne();
    }

    @Test
    public void shouldUpdateDatabaseQueryTimeMetric() {
        // when
        metrics.updateDatabaseQueryTimeMetric(456L);

        // then
        assertThat(metricRegistry.timer("db_query_time").getCount()).isOne();
    }

    @Test
    public void shouldCreateDatabaseCircuitBreakerGaugeMetric() {
        // when
        metrics.createDatabaseCircuitBreakerGauge(() -> true);

        // then
        assertThat(metricRegistry.gauge("circuit-breaker.db.opened.count", () -> null).getValue()).isEqualTo(1L);
    }

    @Test
    public void shouldCreateHttpClientCircuitBreakerGaugeMetric() {
        // when
        metrics.createHttpClientCircuitBreakerGauge("id", () -> true);

        // then
        assertThat(metricRegistry.gauge("circuit-breaker.http.named.id.opened.count", () -> null).getValue())
                .isEqualTo(1L);
    }

    @Test
    public void shouldCreateHttpClientCircuitBreakerNumberGaugeMetric() {
        // when
        metrics.createHttpClientCircuitBreakerNumberGauge(() -> 1);

        // then
        assertThat(metricRegistry.gauge("circuit-breaker.http.existing.count", () -> null).getValue()).isEqualTo(1L);
    }

    @Test
    public void shouldCreateGeoLocationCircuitBreakerGaugeMetric() {
        // when
        metrics.createGeoLocationCircuitBreakerGauge(() -> true);

        // then
        assertThat(metricRegistry.gauge("circuit-breaker.geo.opened.count", () -> null).getValue()).isEqualTo(1L);
    }

    @Test
    public void shouldIncrementBothGeoLocationRequestsAndSuccessfulMetrics() {
        // when
        metrics.updateGeoLocationMetric(true);

        // then
        assertThat(metricRegistry.counter("geolocation_requests").getCount()).isOne();
        assertThat(metricRegistry.counter("geolocation_successful").getCount()).isOne();
    }

    @Test
    public void shouldIncrementBothGeoLocationRequestsAndFailMetrics() {
        // when
        metrics.updateGeoLocationMetric(false);

        // then
        assertThat(metricRegistry.counter("geolocation_requests").getCount()).isOne();
        assertThat(metricRegistry.counter("geolocation_fail").getCount()).isOne();
    }

    @Test
    public void shouldAlwaysIncrementGeoLocationRequestsMetricAndEitherSuccessfulOrFailMetricDependingOnFlag() {
        // when
        metrics.updateGeoLocationMetric(true);
        metrics.updateGeoLocationMetric(false);
        metrics.updateGeoLocationMetric(true);

        // then
        assertThat(metricRegistry.counter("geolocation_fail").getCount()).isOne();
        assertThat(metricRegistry.counter("geolocation_successful").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("geolocation_requests").getCount()).isEqualTo(3);
    }

    @Test
    public void shouldIncrementStoredRequestFoundMetric() {
        // when
        metrics.updateStoredRequestMetric(true);

        // then
        assertThat(metricRegistry.counter("stored_requests_found").getCount()).isOne();
    }

    @Test
    public void shouldIncrementStoredRequestMissingMetric() {
        // when
        metrics.updateStoredRequestMetric(false);

        // then
        assertThat(metricRegistry.counter("stored_requests_missing").getCount()).isOne();
    }

    @Test
    public void shouldIncrementStoredImpFoundMetric() {
        // when
        metrics.updateStoredImpsMetric(true);

        // then
        assertThat(metricRegistry.counter("stored_imps_found").getCount()).isOne();
    }

    @Test
    public void shouldIncrementStoredImpMissingMetric() {
        // when
        metrics.updateStoredImpsMetric(false);

        // then
        assertThat(metricRegistry.counter("stored_imps_missing").getCount()).isOne();
    }

    @Test
    public void shouldIncrementPrebidCacheRequestSuccessTimer() {
        // when
        metrics.updateCacheRequestSuccessTime("accountId", 1424L);

        // then
        assertThat(metricRegistry.timer("prebid_cache.requests.ok").getCount()).isEqualTo(1);
        assertThat(metricRegistry.timer("account.accountId.prebid_cache.requests.ok").getCount()).isOne();
    }

    @Test
    public void shouldIncrementPrebidCacheRequestFailedTimer() {
        // when
        metrics.updateCacheRequestFailedTime("accountId", 1424L);

        // then
        assertThat(metricRegistry.timer("prebid_cache.requests.err").getCount()).isEqualTo(1);
        assertThat(metricRegistry.timer("account.accountId.prebid_cache.requests.err").getCount()).isOne();
    }

    @Test
    public void shouldIncrementPrebidCacheCreativeSizeHistogram() {
        // when
        metrics.updateCacheCreativeSize("accountId", 123, MetricName.json);
        metrics.updateCacheCreativeSize("accountId", 456, MetricName.xml);
        metrics.updateCacheCreativeSize("accountId", 789, MetricName.unknown);

        // then
        assertThat(metricRegistry.histogram("prebid_cache.creative_size.json").getCount()).isEqualTo(1);
        assertThat(metricRegistry.histogram("account.accountId.prebid_cache.creative_size.json").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.histogram("prebid_cache.creative_size.xml").getCount()).isEqualTo(1);
        assertThat(metricRegistry.histogram("account.accountId.prebid_cache.creative_size.xml").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.histogram("prebid_cache.creative_size.unknown").getCount()).isEqualTo(1);
        assertThat(metricRegistry.histogram("account.accountId.prebid_cache.creative_size.unknown").getCount())
                .isEqualTo(1);
    }

    @Test
    public void shouldCreateCurrencyRatesGaugeMetric() {
        // when
        metrics.createCurrencyRatesGauge(() -> true);

        // then
        assertThat(metricRegistry.gauge("currency-rates.stale.count", () -> null).getValue()).isEqualTo(1L);
    }

    @Test
    public void updateSettingsCacheRefreshTimeShouldUpdateTimer() {
        // when
        metrics.updateSettingsCacheRefreshTime(MetricName.stored_request, MetricName.initialize, 123L);

        // then
        assertThat(metricRegistry
                .timer("settings.cache.stored-request.refresh.initialize.db_query_time")
                .getCount())
                .isEqualTo(1);
    }

    @Test
    public void updateSettingsCacheRefreshErrorMetricShouldIncrementMetric() {
        // when
        metrics.updateSettingsCacheRefreshErrorMetric(MetricName.stored_request, MetricName.initialize);

        // then
        assertThat(metricRegistry.counter("settings.cache.stored-request.refresh.initialize.err").getCount())
                .isEqualTo(1);
    }

    @Test
    public void updateSettingsCacheEventMetricShouldIncrementMetric() {
        // when
        metrics.updateSettingsCacheEventMetric(MetricName.account, MetricName.hit);

        // then
        assertThat(metricRegistry.counter("settings.cache.account.hit").getCount()).isEqualTo(1);
    }

    @Test
    public void updateHooksMetricsShouldIncrementMetrics() {
        // when
        metrics.updateHooksMetrics(
                "module1", Stage.entrypoint, "hook1", ExecutionStatus.success, 5L, ExecutionAction.update);
        metrics.updateHooksMetrics(
                "module1", Stage.raw_auction_request, "hook2", ExecutionStatus.success, 5L, ExecutionAction.no_action);
        metrics.updateHooksMetrics(
                "module1",
                Stage.processed_auction_request,
                "hook3",
                ExecutionStatus.success,
                5L,
                ExecutionAction.reject);
        metrics.updateHooksMetrics(
                "module2", Stage.bidder_request, "hook1", ExecutionStatus.failure, 6L, null);
        metrics.updateHooksMetrics(
                "module2", Stage.raw_bidder_response, "hook2", ExecutionStatus.timeout, 7L, null);
        metrics.updateHooksMetrics(
                "module2", Stage.processed_bidder_response, "hook3", ExecutionStatus.execution_failure, 5L, null);
        metrics.updateHooksMetrics(
                "module2", Stage.auction_response, "hook4", ExecutionStatus.invocation_failure, 5L, null);

        // then
        assertThat(metricRegistry.counter("modules.module.module1.stage.entrypoint.hook.hook1.call")
                .getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("modules.module.module1.stage.entrypoint.hook.hook1.success.update")
                .getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("modules.module.module1.stage.entrypoint.hook.hook1.duration").getCount())
                .isEqualTo(1);

        assertThat(metricRegistry.counter("modules.module.module1.stage.rawauction.hook.hook2.call").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("modules.module.module1.stage.rawauction.hook.hook2.success.noop").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("modules.module.module1.stage.rawauction.hook.hook2.duration").getCount())
                .isEqualTo(1);

        assertThat(metricRegistry.counter("modules.module.module1.stage.procauction.hook.hook3.call").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("modules.module.module1.stage.procauction.hook.hook3.success.reject")
                .getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("modules.module.module1.stage.procauction.hook.hook3.duration").getCount())
                .isEqualTo(1);

        assertThat(metricRegistry.counter("modules.module.module2.stage.bidrequest.hook.hook1.call").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("modules.module.module2.stage.bidrequest.hook.hook1.failure").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("modules.module.module2.stage.bidrequest.hook.hook1.duration").getCount())
                .isEqualTo(1);

        assertThat(metricRegistry.counter("modules.module.module2.stage.rawbidresponse.hook.hook2.call").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("modules.module.module2.stage.rawbidresponse.hook.hook2.timeout").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("modules.module.module2.stage.rawbidresponse.hook.hook2.duration").getCount())
                .isEqualTo(1);

        assertThat(metricRegistry.counter("modules.module.module2.stage.procbidresponse.hook.hook3.call").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("modules.module.module2.stage.procbidresponse.hook.hook3.execution-error")
                .getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("modules.module.module2.stage.procbidresponse.hook.hook3.duration").getCount())
                .isEqualTo(1);

        assertThat(metricRegistry.counter("modules.module.module2.stage.auctionresponse.hook.hook4.call").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("modules.module.module2.stage.auctionresponse.hook.hook4.execution-error")
                .getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("modules.module.module2.stage.auctionresponse.hook.hook4.duration").getCount())
                .isEqualTo(1);
    }

    @Test
    public void updateAccountHooksMetricsShouldIncrementMetricsIfVerbosityIsDetailed() {
        // given
        given(accountMetricsVerbosityResolver.forAccount(any())).willReturn(AccountMetricsVerbosityLevel.detailed);

        // when
        metrics.updateAccountHooksMetrics(
                Account.empty("accountId"), "module1", ExecutionStatus.success, ExecutionAction.update);
        metrics.updateAccountHooksMetrics(
                Account.empty("accountId"), "module2", ExecutionStatus.failure, null);
        metrics.updateAccountHooksMetrics(
                Account.empty("accountId"), "module3", ExecutionStatus.timeout, null);

        // then
        assertThat(metricRegistry.counter("account.accountId.modules.module.module1.call").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.modules.module.module1.success.update").getCount())
                .isEqualTo(1);

        assertThat(metricRegistry.counter("account.accountId.modules.module.module2.call").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.modules.module.module2.failure").getCount())
                .isEqualTo(1);

        assertThat(metricRegistry.counter("account.accountId.modules.module.module3.call").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.modules.module.module3.failure").getCount())
                .isEqualTo(1);
    }

    @Test
    public void updateAccountHooksMetricsShouldNotIncrementMetricsIfVerbosityIsNotAtLeastDetailed() {
        // given
        given(accountMetricsVerbosityResolver.forAccount(any())).willReturn(AccountMetricsVerbosityLevel.basic);

        // when
        metrics.updateAccountHooksMetrics(
                Account.empty("accountId"), "module1", ExecutionStatus.success, ExecutionAction.update);

        // then
        assertThat(metricRegistry.counter("account.accountId.modules.module.module1.call").getCount())
                .isZero();
        assertThat(metricRegistry.counter("account.accountId.modules.module.module1.success.update").getCount())
                .isZero();
    }

    @Test
    public void updateAccountModuleDurationMetricShouldIncrementMetricsIfVerbosityIsDetailed() {
        // given
        given(accountMetricsVerbosityResolver.forAccount(any())).willReturn(AccountMetricsVerbosityLevel.detailed);

        // when
        metrics.updateAccountModuleDurationMetric(
                Account.empty("accountId"), "module1", 5L);
        metrics.updateAccountModuleDurationMetric(
                Account.empty("accountId"), "module2", 6L);

        // then
        assertThat(metricRegistry.timer("account.accountId.modules.module.module1.duration").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("account.accountId.modules.module.module2.duration").getCount())
                .isEqualTo(1);
    }

    @Test
    public void updateAccountModuleDurationMetricShouldNotIncrementMetricsIfVerbosityIsNotAtLeastDetailed() {
        // given
        given(accountMetricsVerbosityResolver.forAccount(any())).willReturn(AccountMetricsVerbosityLevel.basic);

        // when
        metrics.updateAccountModuleDurationMetric(
                Account.empty("accountId"), "module1", 5L);

        // then
        assertThat(metricRegistry.timer("account.accountId.modules.module.module1.duration").getCount())
                .isZero();
    }

    @Test
    public void shouldIncrementRequestsActivityDisallowedCount() {
        // when
        metrics.updateRequestsActivityDisallowedCount(Activity.CALL_BIDDER);

        // then
        assertThat(metricRegistry.counter("requests.activity.fetch_bids.disallowed.count").getCount())
                .isEqualTo(1);
    }

    @Test
    public void shouldIncrementUpdateAccountActivityDisallowedCount() {
        // when
        metrics.updateAccountActivityDisallowedCount("account_id", Activity.CALL_BIDDER);

        // then
        assertThat(metricRegistry.counter("account.account_id.activity.fetch_bids.disallowed.count").getCount())
                .isEqualTo(1);
    }

    @Test
    public void shouldIncrementUpdateAdapterActivityDisallowedCount() {
        // when
        metrics.updateAdapterActivityDisallowedCount("adapter", Activity.CALL_BIDDER);

        // then
        assertThat(metricRegistry.counter("adapter.adapter.activity.fetch_bids.disallowed.count").getCount())
                .isEqualTo(1);
    }

    @Test
    public void shouldIncrementUpdateRequestsActivityProcessedRulesCount() {
        // when
        metrics.updateRequestsActivityProcessedRulesCount();

        // then
        assertThat(metricRegistry.counter("requests.activity.processedrules.count").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementUpdateAccountActivityProcessedRulesCount() {
        // when
        metrics.updateAccountActivityProcessedRulesCount("account_id");

        // then
        assertThat(metricRegistry.counter("account.account_id.activity.processedrules.count").getCount())
                .isEqualTo(1);
    }

    @Test
    public void shouldIncrementUpdateAccountRequestRejectedByFailedFetchCount() {
        // when
        metrics.updateAccountRequestRejectedByFailedFetch("account_id");

        // then
        assertThat(metricRegistry.counter("account.account_id.requests.rejected.account-fetch-failed").getCount())
                .isEqualTo(1);
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
            metricsConsumer.accept(new Metrics(metricRegistry, CounterType.valueOf(counterType.name()),
                    accountMetricsVerbosityResolver));

            // then
            softly.assertThat(metricRegistry.getMetrics()).hasValueSatisfying(new Condition<>(
                    metric -> metric.getClass() == counterTypeClasses.get(counterType),
                    null));
        }

        softly.assertAll();
    }
}
