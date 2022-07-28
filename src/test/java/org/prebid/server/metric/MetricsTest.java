package org.prebid.server.metric;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;
import org.prebid.server.settings.model.Account;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Mock
    private AccountMetricsVerbosityResolver accountMetricsVerbosityResolver;

    private CompositeMeterRegistry meterRegistry;

    private Metrics metrics;

    @Before
    public void setUp() {
        meterRegistry = new CompositeMeterRegistry(new MockClock());
        SimpleMeterRegistry simple = new SimpleMeterRegistry();
        meterRegistry.add(simple);

        given(accountMetricsVerbosityResolver.forAccount(any())).willReturn(AccountMetricsVerbosityLevel.detailed);

        metrics = new Metrics(meterRegistry, accountMetricsVerbosityResolver);
    }

    @Test
    public void forAccountShouldReturnSameAccountMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID)).isSameAs(metrics.forAccount(ACCOUNT_ID));
    }

    @Test
    public void forAccountShouldReturnAccountMetricsConfiguredWithAccount() {
        // when
        metrics.forAccount(ACCOUNT_ID).incCounter(MetricName.requests);

        // then
        assertThat(meterRegistry.counter("account.accountId.requests").count()).isOne();
    }

    @Test
    public void forAdapterShouldReturnSameAdapterMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAdapter(RUBICON)).isSameAs(metrics.forAdapter(RUBICON));
    }

    @Test
    public void forAdapterShouldReturnAdapterMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).incCounter(MetricName.bids_received);

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.bids_received").count()).isOne();
    }

    @Test
    public void shouldReturnSameAdapterRequestTypeMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAdapter(RUBICON).requestType(MetricName.amp))
                .isSameAs(metrics.forAdapter(RUBICON).requestType(MetricName.amp));
    }

    @Test
    public void shouldReturnAdapterRequestTypeMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).requestType(MetricName.openrtb2web).incCounter(MetricName.requests);

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.requests.type.openrtb2-web").count()).isOne();
    }

    @Test
    public void shouldReturnSameAdapterRequestMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAdapter(RUBICON).request())
                .isSameAs(metrics.forAdapter(RUBICON).request());
    }

    @Test
    public void shouldReturnAdapterRequestMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).request().incCounter(MetricName.gotbids);

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.requests.gotbids").count()).isOne();
    }

    @Test
    public void shouldReturnSameAccountAdapterMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON))
                .isSameAs(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).incCounter(MetricName.bids_received);

        // then
        assertThat(meterRegistry.counter("account.accountId.adapter.rubicon.bids_received").count()).isOne();
    }

    @Test
    public void shouldReturnSameAccountAdapterRequestMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).request())
                .isSameAs(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).request());
    }

    @Test
    public void shouldReturnAccountAdapterRequestMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).request().incCounter(MetricName.gotbids);

        // then
        assertThat(meterRegistry.counter("account.accountId.adapter.rubicon.requests.gotbids").count())
                .isOne();
    }

    @Test
    public void shouldReturnSameAccountRequestTypeMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).requestType(MetricName.amp))
                .isSameAs(metrics.forAccount(ACCOUNT_ID).requestType(MetricName.amp));
    }

    @Test
    public void shouldReturnAccountRequestTypeMetricsConfiguredWithAccount() {
        // when
        metrics.forAccount(ACCOUNT_ID).requestType(MetricName.openrtb2web).incCounter(MetricName.requests);

        // then
        assertThat(meterRegistry.counter("account.accountId.requests.type.openrtb2-web").count()).isOne();
    }

    @Test
    public void userSyncShouldReturnSameUserSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.userSync()).isSameAs(metrics.userSync());
    }

    @Test
    public void userSyncShouldReturnUserSyncMetricsConfiguredWithPrefix() {
        // when
        metrics.userSync().incCounter(MetricName.opt_outs);

        // then
        assertThat(meterRegistry.counter("usersync.opt_outs").count()).isOne();
    }

    @Test
    public void shouldReturnSameBidderUserSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.userSync().forBidder(RUBICON)).isSameAs(metrics.userSync().forBidder(RUBICON));
    }

    @Test
    public void shouldReturnBidderUserSyncMetricsConfiguredWithBidder() {
        // when
        metrics.userSync().forBidder(RUBICON).incCounter(MetricName.sets);

        // then
        assertThat(meterRegistry.counter("usersync.rubicon.sets").count()).isOne();
    }

    @Test
    public void cookieSyncShouldReturnSameCookieSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.cookieSync()).isSameAs(metrics.cookieSync());
    }

    @Test
    public void cookieSyncShouldReturnCookieSyncMetricsConfiguredWithPrefix() {
        // when
        metrics.cookieSync().incCounter(MetricName.gen);

        // then
        assertThat(meterRegistry.counter("cookie_sync.gen").count()).isOne();
    }

    @Test
    public void shouldReturnSameBidderCookieSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.cookieSync().forBidder(RUBICON)).isSameAs(metrics.cookieSync().forBidder(RUBICON));
    }

    @Test
    public void shouldReturnBidderCookieSyncMetricsConfiguredWithBidder() {
        // when
        metrics.cookieSync().forBidder(RUBICON).incCounter(MetricName.gen);

        // then
        assertThat(meterRegistry.counter("cookie_sync.rubicon.gen").count()).isOne();
    }

    @Test
    public void forRequestTypeShouldReturnSameRequestStatusMetricsOnSuccessiveCalls() {
        assertThat(metrics.forRequestType(MetricName.openrtb2web))
                .isSameAs(metrics.forRequestType(MetricName.openrtb2web));
    }

    @Test
    public void forRequestTypeShouldReturnRequestStatusMetricsConfiguredWithRequestType() {
        // when
        metrics.forRequestType(MetricName.openrtb2web).incCounter(MetricName.ok);

        // then
        assertThat(meterRegistry.counter("requests.ok.openrtb2-web").count()).isOne();
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
        assertThat(meterRegistry.get("circuit-breaker.db.opened.count").gauge().value()).isEqualTo(1L);
    }

    @Test
    public void updateAppAndNoCookieAndImpsRequestedMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(true, false, 1);
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(false, false, 2);
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(false, true, 1);

        // then
        assertThat(meterRegistry.counter("app_requests").count()).isOne();
        assertThat(meterRegistry.counter("no_cookie_requests").count()).isOne();
        assertThat(meterRegistry.counter("imps_requested").count()).isEqualTo(4);
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
        assertThat(meterRegistry.counter("imps_banner").count()).isEqualTo(3);
        assertThat(meterRegistry.counter("imps_video").count()).isEqualTo(5);
        assertThat(meterRegistry.counter("imps_native").count()).isOne();
        assertThat(meterRegistry.counter("imps_audio").count()).isEqualTo(4);
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

        assertThat(meterRegistry.counter("imps_banner").count()).isOne();
        assertThat(meterRegistry.counter("imps_video").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("imps_native").count()).isOne();
        assertThat(meterRegistry.counter("imps_audio").count()).isEqualTo(2);
    }

    @Test
    public void updateRequestTimeMetricShouldUpdateMetric() {
        // when
        metrics.updateRequestTimeMetric(MetricName.request_time, 456L);

        // then
        assertThat(meterRegistry.timer("request_time").count()).isOne();
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
        assertThat(meterRegistry.counter("requests.ok.openrtb2-web").count()).isOne();
        assertThat(meterRegistry.counter("requests.blacklisted_account.openrtb2-web").count()).isOne();
        assertThat(meterRegistry.counter("requests.blacklisted_app.openrtb2-app").count()).isOne();
        assertThat(meterRegistry.counter("requests.err.openrtb2-app").count()).isOne();
        assertThat(meterRegistry.counter("requests.badinput.amp").count()).isOne();
        assertThat(meterRegistry.counter("requests.networkerr.amp").count()).isOne();
    }

    @Test
    public void uupdateRequestBidderCardinalityMetricShouldIncrementMetrics() {
        // when
        metrics.updateRequestBidderCardinalityMetric(3);

        // then
        assertThat(meterRegistry.counter("bidder-cardinality.3.requests").count()).isEqualTo(1);
    }

    @Test
    public void updateAccountRequestMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAccountRequestMetrics(Account.empty(ACCOUNT_ID), MetricName.openrtb2web);

        // then
        assertThat(meterRegistry.counter("account.accountId.requests").count()).isOne();
        assertThat(meterRegistry.counter("account.accountId.requests.type.openrtb2-web").count()).isOne();
    }

    @Test
    public void updateAdapterRequestTypeAndNoCookieMetricsShouldUpdateMetricsAsExpected() {

        // when
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(RUBICON, MetricName.openrtb2app, true);
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(RUBICON, MetricName.amp, false);

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.requests.type.openrtb2-app").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("adapter.rubicon.no_cookie_requests").count()).isOne();
        assertThat(meterRegistry.counter("adapter.rubicon.requests.type.amp").count()).isOne();
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
        assertThat(meterRegistry.counter("analytics.analyticCode.auction.ok").count()).isOne();
        assertThat(meterRegistry.counter("analytics.analyticCode.amp.timeout").count()).isOne();
        assertThat(meterRegistry.counter("analytics.analyticCode.video.err").count()).isOne();
        assertThat(meterRegistry.counter("analytics.analyticCode.cookie_sync.timeout").count()).isOne();
        assertThat(meterRegistry.counter("analytics.analyticCode.event.err").count()).isOne();
        assertThat(meterRegistry.counter("analytics.analyticCode.setuid.badinput").count()).isOne();
    }

    @Test
    public void updateFetchWithFetchResultShouldCreateMetricsAsExpected() {
        // when
        metrics.updatePriceFloorFetchMetric(MetricName.failure);

        // then
        assertThat(meterRegistry.counter("price-floors.fetch.failure").count()).isOne();
    }

    @Test
    public void updatePriceFloorGeneralErrorsShouldCreateMetricsAsExpected() {
        // when
        metrics.updatePriceFloorGeneralAlertsMetric(MetricName.err);

        // then
        assertThat(meterRegistry.counter("price-floors.general.err").count()).isOne();
    }

    @Test
    public void updateAlertsConfigMetricsShouldCreateMetricsAsExpected() {
        // when
        metrics.updateAlertsConfigFailed("accountId", MetricName.price_floors);
        metrics.updateAlertsConfigFailed("anotherId", MetricName.failed);
        metrics.updateAlertsConfigFailed("accountId", MetricName.price_floors);

        // then
        assertThat(meterRegistry.counter("alerts.account_config.accountId.price-floors")
                .count()).isEqualTo(2);
        assertThat(meterRegistry.counter("alerts.account_config.anotherId.failed")
                .count()).isOne();
    }

    @Test
    public void updateAdapterResponseTimeShouldUpdateMetrics() {
        // when
        metrics.updateAdapterResponseTime(RUBICON, Account.empty(ACCOUNT_ID), 500);
        metrics.updateAdapterResponseTime(CONVERSANT, Account.empty(ACCOUNT_ID), 500);
        metrics.updateAdapterResponseTime(CONVERSANT, Account.empty(ACCOUNT_ID), 500);

        // then
        assertThat(meterRegistry.timer("adapter.rubicon.request_time").count()).isOne();
        assertThat(meterRegistry.timer("account.accountId.adapter.rubicon.request_time").count()).isOne();
        assertThat(meterRegistry.timer("adapter.conversant.request_time").count()).isEqualTo(2);
        assertThat(meterRegistry.timer("account.accountId.adapter.conversant.request_time").count()).isEqualTo(2);
    }

    @Test
    public void updateAdapterRequestNobidMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestNobidMetrics(RUBICON, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestNobidMetrics(CONVERSANT, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestNobidMetrics(CONVERSANT, Account.empty(ACCOUNT_ID));

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.requests.nobid").count()).isOne();
        assertThat(meterRegistry.counter("account.accountId.adapter.rubicon.requests.nobid").count()).isOne();
        assertThat(meterRegistry.counter("adapter.conversant.requests.nobid").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("account.accountId.adapter.conversant.requests.nobid").count())
                .isEqualTo(2);
    }

    @Test
    public void updateAdapterRequestGotbidsMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestGotbidsMetrics(CONVERSANT, Account.empty(ACCOUNT_ID));
        metrics.updateAdapterRequestGotbidsMetrics(CONVERSANT, Account.empty(ACCOUNT_ID));

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.requests.gotbids").count()).isOne();
        assertThat(meterRegistry.counter("account.accountId.adapter.rubicon.requests.gotbids").count())
                .isOne();
        assertThat(meterRegistry.counter("adapter.conversant.requests.gotbids").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("account.accountId.adapter.conversant.requests.gotbids").count())
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
        assertThat(meterRegistry.summary("adapter.rubicon.prices").count()).isEqualTo(2);
        assertThat(meterRegistry.summary("account.accountId.adapter.rubicon.prices").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("adapter.rubicon.bids_received").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("account.accountId.adapter.rubicon.bids_received").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("adapter.rubicon.banner.adm_bids_received").count()).isOne();
        assertThat(meterRegistry.counter("adapter.rubicon.video.nurl_bids_received").count()).isOne();
        assertThat(meterRegistry.summary("adapter.conversant.prices").count()).isEqualTo(2);
        assertThat(meterRegistry.summary("account.accountId.adapter.conversant.prices").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("adapter.conversant.bids_received").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("account.accountId.adapter.conversant.bids_received").count())
                .isEqualTo(2);
        assertThat(meterRegistry.counter("adapter.conversant.banner.nurl_bids_received").count()).isEqualTo(2);
    }

    @Test
    public void updateAdapterRequestErrorMetricShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestErrorMetric(RUBICON, MetricName.badinput);
        metrics.updateAdapterRequestErrorMetric(CONVERSANT, MetricName.badinput);
        metrics.updateAdapterRequestErrorMetric(CONVERSANT, MetricName.badinput);

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.requests.badinput").count()).isOne();
        assertThat(meterRegistry.counter("adapter.conversant.requests.badinput").count()).isEqualTo(2);
    }

    @Test
    public void updateSizeValidationMetricsShouldIncrementMetrics() {
        // when
        metrics.updateSizeValidationMetrics(RUBICON, ACCOUNT_ID, MetricName.err);
        metrics.updateSizeValidationMetrics(CONVERSANT, ACCOUNT_ID, MetricName.err);

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.response.validation.size.err").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("adapter.conversant.response.validation.size.err").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("account.accountId.response.validation.size.err").count()).isEqualTo(2);
    }

    @Test
    public void updateSecureValidationMetricsShouldIncrementMetrics() {
        // when
        metrics.updateSecureValidationMetrics(RUBICON, ACCOUNT_ID, MetricName.err);
        metrics.updateSecureValidationMetrics(CONVERSANT, ACCOUNT_ID, MetricName.err);

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.response.validation.secure.err").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("adapter.conversant.response.validation.secure.err").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("account.accountId.response.validation.secure.err").count()).isEqualTo(2);
    }

    @Test
    public void updateCookieSyncRequestMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncRequestMetric();

        // then
        assertThat(meterRegistry.counter("cookie_sync_requests").count()).isOne();
    }

    @Test
    public void updateUserSyncOptoutMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncOptoutMetric();

        // then
        assertThat(meterRegistry.counter("usersync.opt_outs").count()).isOne();
    }

    @Test
    public void updateUserSyncBadRequestMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncBadRequestMetric();

        // then
        assertThat(meterRegistry.counter("usersync.bad_requests").count()).isOne();
    }

    @Test
    public void updateUserSyncSetsMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncSetsMetric(RUBICON);

        // then
        assertThat(meterRegistry.counter("usersync.rubicon.sets").count()).isOne();
    }

    @Test
    public void updateUserSyncTcfBlockedMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncTcfBlockedMetric(RUBICON);

        // then
        assertThat(meterRegistry.counter("usersync.rubicon.tcf.blocked").count()).isOne();
    }

    @Test
    public void updateCookieSyncTcfBlockedMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncTcfBlockedMetric(RUBICON);
        metrics.updateCookieSyncTcfBlockedMetric(CONVERSANT);
        metrics.updateCookieSyncTcfBlockedMetric(CONVERSANT);

        // then
        assertThat(meterRegistry.counter("cookie_sync.rubicon.tcf.blocked").count()).isOne();
        assertThat(meterRegistry.counter("cookie_sync.conversant.tcf.blocked").count()).isEqualTo(2);
    }

    @Test
    public void updateCookieSyncGenMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncGenMetric(RUBICON);

        // then
        assertThat(meterRegistry.counter("cookie_sync.rubicon.gen").count()).isOne();
    }

    @Test
    public void updateCookieSyncMatchesMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncMatchesMetric(RUBICON);

        // then
        assertThat(meterRegistry.counter("cookie_sync.rubicon.matches").count()).isOne();
    }

    @Test
    public void updateGpRequestMetricShouldIncrementPlannerRequestAndPlannerSuccessfulRequest() {
        // when
        metrics.updatePlannerRequestMetric(true);

        // then
        assertThat(meterRegistry.counter("pg.planner_requests").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("pg.planner_request_successful").count()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementPlannerRequestAndPlannerFailedRequest() {
        // when
        metrics.updatePlannerRequestMetric(false);

        // then
        assertThat(meterRegistry.counter("pg.planner_requests").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("pg.planner_request_failed").count()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementUserDetailsSuccessfulRequest() {
        // when
        metrics.updateUserDetailsRequestMetric(true);

        // then
        assertThat(meterRegistry.counter("user_details_requests").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("user_details_request_successful").count()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementUserDetailsFailedRequest() {
        // when
        metrics.updateUserDetailsRequestMetric(false);

        // then
        assertThat(meterRegistry.counter("user_details_requests").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("user_details_request_failed").count()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementWinSuccessfulRequest() {
        // when
        metrics.updateWinEventRequestMetric(true);

        // then
        assertThat(meterRegistry.counter("win_requests").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("win_request_successful").count()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementWinFailedRequest() {
        // when
        metrics.updateWinEventRequestMetric(false);

        // then
        assertThat(meterRegistry.counter("win_requests").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("win_request_failed").count()).isEqualTo(1);
    }

    @Test
    public void updateWinRequestTimeShouldLogTime() {
        // when
        metrics.updateWinRequestTime(20L);

        // then
        assertThat(meterRegistry.timer("win_request_time").count()).isEqualTo(1);
    }

    @Test
    public void updateWinRequestPreparationFailedShouldIncrementMetric() {
        // when
        metrics.updateWinRequestPreparationFailed();

        // then
        assertThat(meterRegistry.counter("win_request_preparation_failed").count()).isEqualTo(1);
    }

    @Test
    public void updateUserDetailsRequestPreparationFailedShouldIncrementMetric() {
        // when
        metrics.updateUserDetailsRequestPreparationFailed();

        // then
        assertThat(meterRegistry.counter("user_details_request_preparation_failed").count()).isEqualTo(1);
    }

    @Test
    public void updateDeliveryRequestMetricShouldIncrementDeliveryRequestAndSuccessfulDeliveryRequest() {
        // when
        metrics.updateDeliveryRequestMetric(true);

        // then
        assertThat(meterRegistry.counter("pg.delivery_requests").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("pg.delivery_request_successful").count()).isEqualTo(1);
    }

    @Test
    public void updateDeliveryRequestMetricShouldIncrementDeliveryRequestAndFailedDeliveryRequest() {
        // when
        metrics.updateDeliveryRequestMetric(false);

        // then
        assertThat(meterRegistry.counter("pg.delivery_requests").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("pg.delivery_request_failed").count()).isEqualTo(1);
    }

    @Test
    public void updateLineItemsNumberMetricShouldIncrementLineItemsNumberForAAcountValue() {
        // when
        metrics.updateLineItemsNumberMetric(20L);

        // then
        assertThat(meterRegistry.counter("pg.planner_lineitems_received").count()).isEqualTo(20);
    }

    @Test
    public void updatePlannerRequestTimeShouldLogTime() {
        // when
        metrics.updatePlannerRequestTime(20L);

        // then
        assertThat(meterRegistry.timer("pg.planner_request_time").count()).isEqualTo(1);
    }

    @Test
    public void updateDeliveryRequestTimeShouldLogTime() {
        // when
        metrics.updateDeliveryRequestTime(20L);

        // then
        assertThat(meterRegistry.timer("pg.delivery_request_time").count()).isEqualTo(1);
    }

    @Test
    public void updateAuctionTcfMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAuctionTcfMetrics(RUBICON, MetricName.openrtb2web, true, true, true, true);
        metrics.updateAuctionTcfMetrics(CONVERSANT, MetricName.openrtb2web, false, true, true, false);
        metrics.updateAuctionTcfMetrics(CONVERSANT, MetricName.openrtb2app, true, false, false, true);

        // then
        assertThat(meterRegistry.counter("adapter.rubicon.openrtb2-web.tcf.userid_removed").count()).isOne();
        assertThat(meterRegistry.counter("adapter.rubicon.openrtb2-web.tcf.geo_masked").count()).isOne();
        assertThat(meterRegistry.counter("adapter.rubicon.openrtb2-web.tcf.analytics_blocked").count()).isOne();
        assertThat(meterRegistry.counter("adapter.rubicon.openrtb2-web.tcf.request_blocked").count()).isOne();
        assertThat(meterRegistry.counter("adapter.conversant.openrtb2-web.tcf.geo_masked").count()).isOne();
        assertThat(meterRegistry.counter("adapter.conversant.openrtb2-web.tcf.analytics_blocked").count()).isOne();
        assertThat(meterRegistry.counter("adapter.conversant.openrtb2-app.tcf.userid_removed").count()).isOne();
        assertThat(meterRegistry.counter("adapter.conversant.openrtb2-app.tcf.request_blocked").count()).isOne();
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
        assertThat(meterRegistry.counter("privacy.coppa").count()).isOne();
    }

    @Test
    public void updatePrivacyLmtMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyLmtMetric();

        // then
        assertThat(meterRegistry.counter("privacy.lmt").count()).isOne();
    }

    @Test
    public void updatePrivacyCcpaMetricsShouldIncrementMetrics() {
        // when
        metrics.updatePrivacyCcpaMetrics(true, true);

        // then
        assertThat(meterRegistry.counter("privacy.usp.specified").count()).isOne();
        assertThat(meterRegistry.counter("privacy.usp.opt-out").count()).isOne();
    }

    @Test
    public void updatePrivacyTcfMissingMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfMissingMetric();

        // then
        assertThat(meterRegistry.counter("privacy.tcf.missing").count()).isOne();
    }

    @Test
    public void updatePrivacyTcfInvalidMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfInvalidMetric();

        // then
        assertThat(meterRegistry.counter("privacy.tcf.invalid").count()).isOne();
    }

    @Test
    public void updatePrivacyTcfRequestsMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfRequestsMetric(1);

        // then
        assertThat(meterRegistry.counter("privacy.tcf.v1.requests").count()).isOne();
    }

    @Test
    public void updatePrivacyTcfGeoMetricShouldIncrementMetrics() {
        // when
        metrics.updatePrivacyTcfGeoMetric(1, null);
        metrics.updatePrivacyTcfGeoMetric(2, true);
        metrics.updatePrivacyTcfGeoMetric(2, false);

        // then
        assertThat(meterRegistry.counter("privacy.tcf.v1.unknown-geo").count()).isOne();
        assertThat(meterRegistry.counter("privacy.tcf.v2.in-geo").count()).isOne();
        assertThat(meterRegistry.counter("privacy.tcf.v2.out-geo").count()).isOne();
    }

    @Test
    public void updatePrivacyTcfVendorListMissingMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfVendorListMissingMetric(1);

        // then
        assertThat(meterRegistry.counter("privacy.tcf.v1.vendorlist.missing").count()).isOne();
    }

    @Test
    public void updatePrivacyTcfVendorListOkMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfVendorListOkMetric(1);

        // then
        assertThat(meterRegistry.counter("privacy.tcf.v1.vendorlist.ok").count()).isOne();
    }

    @Test
    public void updatePrivacyTcfVendorListErrorMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfVendorListErrorMetric(1);

        // then
        assertThat(meterRegistry.counter("privacy.tcf.v1.vendorlist.err").count()).isOne();
    }

    @Test
    public void updatePrivacyTcfVendorListFallbackMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfVendorListFallbackMetric(1);

        // then
        assertThat(meterRegistry.counter("privacy.tcf.v1.vendorlist.fallback").count()).isEqualTo(1);
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
        assertThat(meterRegistry.counter("account.accountId.requests").count()).isZero();
        assertThat(meterRegistry.counter("account.accountId.requests.type.openrtb2-web").count()).isZero();
        assertThat(meterRegistry.timer("account.accountId.rubicon.request_time").count()).isZero();
        assertThat(meterRegistry.counter("account.accountId.rubicon.requests.nobid").count()).isZero();
        assertThat(meterRegistry.counter("account.accountId.rubicon.requests.gotbids").count()).isZero();
        assertThat(meterRegistry.summary("account.accountId.rubicon.prices").count()).isZero();
        assertThat(meterRegistry.counter("account.accountId.rubicon.bids_received").count()).isZero();
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
        assertThat(meterRegistry.counter("account.accountId.requests").count()).isOne();
        assertThat(meterRegistry.counter("account.accountId.requests.type.openrtb2-web").count()).isZero();
        assertThat(meterRegistry.timer("account.accountId.rubicon.request_time").count()).isZero();
        assertThat(meterRegistry.counter("account.accountId.rubicon.requests.nobid").count()).isZero();
        assertThat(meterRegistry.counter("account.accountId.rubicon.requests.gotbids").count()).isZero();
        assertThat(meterRegistry.summary("account.accountId.rubicon.prices").count()).isZero();
        assertThat(meterRegistry.counter("account.accountId.rubicon.bids_received").count()).isZero();
    }

    @Test
    public void shouldIncrementConnectionAcceptErrorsMetric() {
        // when
        metrics.updateConnectionAcceptErrors();

        // then
        assertThat(meterRegistry.counter("connection_accept_errors").count()).isOne();
    }

    @Test
    public void shouldUpdateDatabaseQueryTimeMetric() {
        // when
        metrics.updateDatabaseQueryTimeMetric(456L);

        // then
        assertThat(meterRegistry.timer("db_query_time").count()).isOne();
    }

    @Test
    public void shouldCreateDatabaseCircuitBreakerGaugeMetric() {
        // when
        metrics.createDatabaseCircuitBreakerGauge(() -> true);

        // then
        assertThat(meterRegistry.get("circuit-breaker.db.opened.count").gauge().value()).isEqualTo(1L);
    }

    @Test
    public void shouldCreateHttpClientCircuitBreakerGaugeMetric() {
        // when
        metrics.createHttpClientCircuitBreakerGauge("id", () -> true);

        // then
        assertThat(meterRegistry.get("circuit-breaker.http.named.id.opened.count").gauge().value())
                .isEqualTo(1L);
    }

    @Test
    public void shouldCreateHttpClientCircuitBreakerNumberGaugeMetric() {
        // when
        metrics.createHttpClientCircuitBreakerNumberGauge(() -> 1);

        // then
        assertThat(meterRegistry.get("circuit-breaker.http.existing.count").gauge().value()).isEqualTo(1L);
    }

    @Test
    public void shouldCreateGeoLocationCircuitBreakerGaugeMetric() {
        // when
        metrics.createGeoLocationCircuitBreakerGauge(() -> true);

        // then
        assertThat(meterRegistry.get("circuit-breaker.geo.opened.count").gauge().value()).isEqualTo(1L);
    }

    @Test
    public void shouldIncrementBothGeoLocationRequestsAndSuccessfulMetrics() {
        // when
        metrics.updateGeoLocationMetric(true);

        // then
        assertThat(meterRegistry.counter("geolocation_requests").count()).isOne();
        assertThat(meterRegistry.counter("geolocation_successful").count()).isOne();
    }

    @Test
    public void shouldIncrementBothGeoLocationRequestsAndFailMetrics() {
        // when
        metrics.updateGeoLocationMetric(false);

        // then
        assertThat(meterRegistry.counter("geolocation_requests").count()).isOne();
        assertThat(meterRegistry.counter("geolocation_fail").count()).isOne();
    }

    @Test
    public void shouldAlwaysIncrementGeoLocationRequestsMetricAndEitherSuccessfulOrFailMetricDependingOnFlag() {
        // when
        metrics.updateGeoLocationMetric(true);
        metrics.updateGeoLocationMetric(false);
        metrics.updateGeoLocationMetric(true);

        // then
        assertThat(meterRegistry.counter("geolocation_fail").count()).isOne();
        assertThat(meterRegistry.counter("geolocation_successful").count()).isEqualTo(2);
        assertThat(meterRegistry.counter("geolocation_requests").count()).isEqualTo(3);
    }

    @Test
    public void shouldIncrementStoredRequestFoundMetric() {
        // when
        metrics.updateStoredRequestMetric(true);

        // then
        assertThat(meterRegistry.counter("stored_requests_found").count()).isOne();
    }

    @Test
    public void shouldIncrementStoredRequestMissingMetric() {
        // when
        metrics.updateStoredRequestMetric(false);

        // then
        assertThat(meterRegistry.counter("stored_requests_missing").count()).isOne();
    }

    @Test
    public void shouldIncrementStoredImpFoundMetric() {
        // when
        metrics.updateStoredImpsMetric(true);

        // then
        assertThat(meterRegistry.counter("stored_imps_found").count()).isOne();
    }

    @Test
    public void shouldIncrementStoredImpMissingMetric() {
        // when
        metrics.updateStoredImpsMetric(false);

        // then
        assertThat(meterRegistry.counter("stored_imps_missing").count()).isOne();
    }

    @Test
    public void shouldIncrementPrebidCacheRequestSuccessTimer() {
        // when
        metrics.updateCacheRequestSuccessTime("accountId", 1424L);

        // then
        assertThat(meterRegistry.timer("prebid_cache.requests.ok").count()).isEqualTo(1);
        assertThat(meterRegistry.timer("account.accountId.prebid_cache.requests.ok").count()).isOne();
    }

    @Test
    public void shouldIncrementPrebidCacheRequestFailedTimer() {
        // when
        metrics.updateCacheRequestFailedTime("accountId", 1424L);

        // then
        assertThat(meterRegistry.timer("prebid_cache.requests.err").count()).isEqualTo(1);
        assertThat(meterRegistry.timer("account.accountId.prebid_cache.requests.err").count()).isOne();
    }

    @Test
    public void shouldIncrementPrebidCacheCreativeSizeHistogram() {
        // when
        metrics.updateCacheCreativeSize("accountId", 123, MetricName.json);
        metrics.updateCacheCreativeSize("accountId", 456, MetricName.xml);
        metrics.updateCacheCreativeSize("accountId", 789, MetricName.unknown);

        // then
        assertThat(meterRegistry.summary("prebid_cache.creative_size.json").count()).isEqualTo(1);
        assertThat(meterRegistry.summary("account.accountId.prebid_cache.creative_size.json").count())
                .isEqualTo(1);
        assertThat(meterRegistry.summary("prebid_cache.creative_size.xml").count()).isEqualTo(1);
        assertThat(meterRegistry.summary("account.accountId.prebid_cache.creative_size.xml").count())
                .isEqualTo(1);
        assertThat(meterRegistry.summary("prebid_cache.creative_size.unknown").count()).isEqualTo(1);
        assertThat(meterRegistry.summary("account.accountId.prebid_cache.creative_size.unknown").count())
                .isEqualTo(1);
    }

    @Test
    public void shouldCreateCurrencyRatesGaugeMetric() {
        // when
        metrics.createCurrencyRatesGauge(() -> true);

        // then
        assertThat(meterRegistry.get("currency-rates.stale.count").gauge().value()).isEqualTo(1L);
    }

    @Test
    public void updateSettingsCacheRefreshTimeShouldUpdateTimer() {
        // when
        metrics.updateSettingsCacheRefreshTime(MetricName.stored_request, MetricName.initialize, 123L);

        // then
        assertThat(meterRegistry
                .timer("settings.cache.stored-request.refresh.initialize.db_query_time")
                .count())
                .isEqualTo(1);
    }

    @Test
    public void updateSettingsCacheRefreshErrorMetricShouldIncrementMetric() {
        // when
        metrics.updateSettingsCacheRefreshErrorMetric(MetricName.stored_request, MetricName.initialize);

        // then
        assertThat(meterRegistry.counter("settings.cache.stored-request.refresh.initialize.err").count())
                .isEqualTo(1);
    }

    @Test
    public void updateSettingsCacheEventMetricShouldIncrementMetric() {
        // when
        metrics.updateSettingsCacheEventMetric(MetricName.account, MetricName.hit);

        // then
        assertThat(meterRegistry.counter("settings.cache.account.hit").count()).isEqualTo(1);
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
        assertThat(meterRegistry.counter("modules.module.module1.stage.entrypoint.hook.hook1.call")
                .count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("modules.module.module1.stage.entrypoint.hook.hook1.success.update")
                .count())
                .isEqualTo(1);
        assertThat(meterRegistry.timer("modules.module.module1.stage.entrypoint.hook.hook1.duration").count())
                .isEqualTo(1);

        assertThat(meterRegistry.counter("modules.module.module1.stage.rawauction.hook.hook2.call").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("modules.module.module1.stage.rawauction.hook.hook2.success.noop").count())
                .isEqualTo(1);
        assertThat(meterRegistry.timer("modules.module.module1.stage.rawauction.hook.hook2.duration").count())
                .isEqualTo(1);

        assertThat(meterRegistry.counter("modules.module.module1.stage.procauction.hook.hook3.call").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("modules.module.module1.stage.procauction.hook.hook3.success.reject")
                .count())
                .isEqualTo(1);
        assertThat(meterRegistry.timer("modules.module.module1.stage.procauction.hook.hook3.duration").count())
                .isEqualTo(1);

        assertThat(meterRegistry.counter("modules.module.module2.stage.bidrequest.hook.hook1.call").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("modules.module.module2.stage.bidrequest.hook.hook1.failure").count())
                .isEqualTo(1);
        assertThat(meterRegistry.timer("modules.module.module2.stage.bidrequest.hook.hook1.duration").count())
                .isEqualTo(1);

        assertThat(meterRegistry.counter("modules.module.module2.stage.rawbidresponse.hook.hook2.call").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("modules.module.module2.stage.rawbidresponse.hook.hook2.timeout").count())
                .isEqualTo(1);
        assertThat(meterRegistry.timer("modules.module.module2.stage.rawbidresponse.hook.hook2.duration").count())
                .isEqualTo(1);

        assertThat(meterRegistry.counter("modules.module.module2.stage.procbidresponse.hook.hook3.call").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("modules.module.module2.stage.procbidresponse.hook.hook3.execution-error")
                .count())
                .isEqualTo(1);
        assertThat(meterRegistry.timer("modules.module.module2.stage.procbidresponse.hook.hook3.duration").count())
                .isEqualTo(1);

        assertThat(meterRegistry.counter("modules.module.module2.stage.auctionresponse.hook.hook4.call").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("modules.module.module2.stage.auctionresponse.hook.hook4.execution-error")
                .count())
                .isEqualTo(1);
        assertThat(meterRegistry.timer("modules.module.module2.stage.auctionresponse.hook.hook4.duration").count())
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
        assertThat(meterRegistry.counter("account.accountId.modules.module.module1.call").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("account.accountId.modules.module.module1.success.update").count())
                .isEqualTo(1);

        assertThat(meterRegistry.counter("account.accountId.modules.module.module2.call").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("account.accountId.modules.module.module2.failure").count())
                .isEqualTo(1);

        assertThat(meterRegistry.counter("account.accountId.modules.module.module3.call").count())
                .isEqualTo(1);
        assertThat(meterRegistry.counter("account.accountId.modules.module.module3.failure").count())
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
        assertThat(meterRegistry.counter("account.accountId.modules.module.module1.call").count())
                .isZero();
        assertThat(meterRegistry.counter("account.accountId.modules.module.module1.success.update").count())
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
        assertThat(meterRegistry.timer("account.accountId.modules.module.module1.duration").count())
                .isEqualTo(1);
        assertThat(meterRegistry.timer("account.accountId.modules.module.module2.duration").count())
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
        assertThat(meterRegistry.timer("account.accountId.modules.module.module1.duration").count())
                .isZero();
    }

    @Test
    public void shouldIncrementWinNotificationMetric() {
        // when
        metrics.updateWinNotificationMetric();

        // then
        assertThat(meterRegistry.counter("win_notifications").count()).isEqualTo(1);
    }
}
