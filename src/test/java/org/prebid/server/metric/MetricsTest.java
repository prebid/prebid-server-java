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
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
    private AccountMetricsVerbosity accountMetricsVerbosity;

    private Metrics metrics;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.DETAILED);

        metrics = new Metrics(metricRegistry, CounterType.COUNTER, accountMetricsVerbosity);
    }

    @Test
    public void createShouldReturnMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics.incCounter(MetricName.BIDS_RECEIVED));
    }

    @Test
    public void forAccountShouldReturnSameAccountMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID)).isSameAs(metrics.forAccount(ACCOUNT_ID));
    }

    @Test
    public void forAccountShouldReturnAccountMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics.forAccount(ACCOUNT_ID).incCounter(MetricName.REQUESTS));
    }

    @Test
    public void forAccountShouldReturnAccountMetricsConfiguredWithAccount() {
        // when
        metrics.forAccount(ACCOUNT_ID).incCounter(MetricName.REQUESTS);

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
                metrics -> metrics.forAdapter(RUBICON).incCounter(MetricName.BIDS_RECEIVED));
    }

    @Test
    public void forAdapterShouldReturnAdapterMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).incCounter(MetricName.BIDS_RECEIVED);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.bids_received").getCount()).isOne();
    }

    @Test
    public void shouldReturnSameAdapterRequestTypeMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAdapter(RUBICON).requestType(MetricName.AMP))
                .isSameAs(metrics.forAdapter(RUBICON).requestType(MetricName.AMP));
    }

    @Test
    public void shouldReturnAdapterRequestTypeMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAdapter(RUBICON)
                .requestType(MetricName.OPENRTB2_APP)
                .incCounter(MetricName.REQUESTS));
    }

    @Test
    public void shouldReturnAdapterRequestTypeMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).requestType(MetricName.OPENRTB2_WEB).incCounter(MetricName.REQUESTS);

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
                .incCounter(MetricName.GOTBIDS));
    }

    @Test
    public void shouldReturnAdapterRequestMetricsConfiguredWithAdapterType() {
        // when
        metrics.forAdapter(RUBICON).request().incCounter(MetricName.GOTBIDS);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.gotbids").getCount()).isOne();
    }

    @Test
    public void shouldReturnSameAccountAdapterMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON))
                .isSameAs(metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount(ACCOUNT_ID)
                .adapter()
                .forAdapter(RUBICON)
                .incCounter(MetricName.BIDS_RECEIVED));
    }

    @Test
    public void shouldReturnAccountAdapterMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).incCounter(MetricName.BIDS_RECEIVED);

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
                .incCounter(MetricName.GOTBIDS));
    }

    @Test
    public void shouldReturnAccountAdapterRequestMetricsConfiguredWithAccountAndAdapterType() {
        // when
        metrics.forAccount(ACCOUNT_ID).adapter().forAdapter(RUBICON).request().incCounter(MetricName.GOTBIDS);

        // then
        assertThat(metricRegistry.counter("account.accountId.adapter.rubicon.requests.gotbids").getCount())
                .isOne();
    }

    @Test
    public void shouldReturnSameAccountRequestTypeMetricsOnSuccessiveCalls() {
        assertThat(metrics.forAccount(ACCOUNT_ID).requestType(MetricName.AMP))
                .isSameAs(metrics.forAccount(ACCOUNT_ID).requestType(MetricName.AMP));
    }

    @Test
    public void shouldReturnAccountRequestTypeMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forAccount(ACCOUNT_ID)
                .requestType(MetricName.OPENRTB2_APP)
                .incCounter(MetricName.REQUESTS));
    }

    @Test
    public void shouldReturnAccountRequestTypeMetricsConfiguredWithAccount() {
        // when
        metrics.forAccount(ACCOUNT_ID).requestType(MetricName.OPENRTB2_WEB).incCounter(MetricName.REQUESTS);

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
                metrics -> metrics.userSync().incCounter(MetricName.OPT_OUTS));
    }

    @Test
    public void userSyncShouldReturnUserSyncMetricsConfiguredWithPrefix() {
        // when
        metrics.userSync().incCounter(MetricName.OPT_OUTS);

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
                .incCounter(MetricName.SETS));
    }

    @Test
    public void shouldReturnBidderUserSyncMetricsConfiguredWithBidder() {
        // when
        metrics.userSync().forBidder(RUBICON).incCounter(MetricName.SETS);

        // then
        assertThat(metricRegistry.counter("usersync.rubicon.sets").getCount()).isOne();
    }

    @Test
    public void cookieSyncShouldReturnSameCookieSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.cookieSync()).isSameAs(metrics.cookieSync());
    }

    @Test
    public void cookieSyncShouldReturnCookieSyncMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(
                metrics -> metrics.cookieSync().incCounter(MetricName.GEN));
    }

    @Test
    public void cookieSyncShouldReturnCookieSyncMetricsConfiguredWithPrefix() {
        // when
        metrics.cookieSync().incCounter(MetricName.GEN);

        // then
        assertThat(metricRegistry.counter("cookie_sync.gen").getCount()).isOne();
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
                .incCounter(MetricName.GEN));
    }

    @Test
    public void shouldReturnBidderCookieSyncMetricsConfiguredWithBidder() {
        // when
        metrics.cookieSync().forBidder(RUBICON).incCounter(MetricName.GEN);

        // then
        assertThat(metricRegistry.counter("cookie_sync.rubicon.gen").getCount()).isOne();
    }

    @Test
    public void forRequestTypeShouldReturnSameRequestStatusMetricsOnSuccessiveCalls() {
        assertThat(metrics.forRequestType(MetricName.OPENRTB2_WEB))
                .isSameAs(metrics.forRequestType(MetricName.OPENRTB2_WEB));
    }

    @Test
    public void forRequestTypeShouldReturnRequestStatusMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(metrics -> metrics
                .forRequestType(MetricName.OPENRTB2_WEB)
                .incCounter(MetricName.OK));
    }

    @Test
    public void forRequestTypeShouldReturnRequestStatusMetricsConfiguredWithRequestType() {
        // when
        metrics.forRequestType(MetricName.OPENRTB2_WEB).incCounter(MetricName.OK);

        // then
        assertThat(metricRegistry.counter("requests.ok.openrtb2-web").getCount()).isOne();
    }

    @Test
    public void forCircuitBreakerShouldReturnSameCircuitBreakerMetricsOnSuccessiveCalls() {
        assertThat(metrics.forCircuitBreakerType(MetricName.DB)).isSameAs(metrics.forCircuitBreakerType(MetricName.DB));
    }

    @Test
    public void forCircuitBreakerShouldReturnCircuitBreakerMetricsConfiguredWithId() {
        // when
        metrics.forCircuitBreakerType(MetricName.DB).createGauge(MetricName.OPENED, () -> 1);

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
        metrics.updateRequestTimeMetric(MetricName.REQUEST_TIME, 456L);

        // then
        assertThat(metricRegistry.timer("request_time").getCount()).isOne();
    }

    @Test
    public void updateRequestTypeMetricShouldIncrementMetric() {
        // when
        metrics.updateRequestTypeMetric(MetricName.OPENRTB2_WEB, MetricName.OK);
        metrics.updateRequestTypeMetric(MetricName.OPENRTB2_WEB, MetricName.BLACKLISTED_ACCOUNT);
        metrics.updateRequestTypeMetric(MetricName.OPENRTB2_APP, MetricName.BLACKLISTED_APP);
        metrics.updateRequestTypeMetric(MetricName.OPENRTB2_APP, MetricName.ERR);
        metrics.updateRequestTypeMetric(MetricName.AMP, MetricName.BADINPUT);
        metrics.updateRequestTypeMetric(MetricName.AMP, MetricName.NETWORKERR);

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
        metrics.updateAccountRequestMetrics(ACCOUNT_ID, MetricName.OPENRTB2_WEB);

        // then
        assertThat(metricRegistry.counter("account.accountId.requests").getCount()).isOne();
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isOne();
    }

    @Test
    public void updateAdapterRequestTypeAndNoCookieMetricsShouldUpdateMetricsAsExpected() {

        // when
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(RUBICON, MetricName.OPENRTB2_APP, true);
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(RUBICON, MetricName.AMP, false);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.openrtb2-app").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.no_cookie_requests").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.amp").getCount()).isOne();
    }

    @Test
    public void updateAnalyticWithEventTypeShouldUpdateMetricsAsExpected() {

        // when
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.EVENT_AUCTION, MetricName.OK);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.EVENT_AMP, MetricName.TIMEOUT);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.EVENT_VIDEO, MetricName.ERR);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.EVENT_COOKIE_SYNC, MetricName.TIMEOUT);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.EVENT_NOTIFICATION, MetricName.ERR);
        metrics.updateAnalyticEventMetric(ANALYTIC_CODE, MetricName.EVENT_SETUID, MetricName.BADINPUT);

        // then
        assertThat(metricRegistry.counter("analytics.analyticCode.auction.ok").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.amp.timeout").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.video.err").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.cookie_sync.timeout").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.event.err").getCount()).isOne();
        assertThat(metricRegistry.counter("analytics.analyticCode.setuid.badinput").getCount()).isOne();
    }

    @Test
    public void updateAdapterResponseTimeShouldUpdateMetrics() {
        // when
        metrics.updateAdapterResponseTime(RUBICON, ACCOUNT_ID, 500);
        metrics.updateAdapterResponseTime(CONVERSANT, ACCOUNT_ID, 500);
        metrics.updateAdapterResponseTime(CONVERSANT, ACCOUNT_ID, 500);

        // then
        assertThat(metricRegistry.timer("adapter.rubicon.request_time").getCount()).isOne();
        assertThat(metricRegistry.timer("account.accountId.adapter.rubicon.request_time").getCount()).isOne();
        assertThat(metricRegistry.timer("adapter.conversant.request_time").getCount()).isEqualTo(2);
        assertThat(metricRegistry.timer("account.accountId.adapter.conversant.request_time").getCount()).isEqualTo(2);
    }

    @Test
    public void updateAdapterRequestNobidMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAdapterRequestNobidMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterRequestNobidMetrics(CONVERSANT, ACCOUNT_ID);
        metrics.updateAdapterRequestNobidMetrics(CONVERSANT, ACCOUNT_ID);

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
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterRequestGotbidsMetrics(CONVERSANT, ACCOUNT_ID);
        metrics.updateAdapterRequestGotbidsMetrics(CONVERSANT, ACCOUNT_ID);

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
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, true, "banner");
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, false, "video");
        metrics.updateAdapterBidMetrics(CONVERSANT, ACCOUNT_ID, 1234L, false, "banner");
        metrics.updateAdapterBidMetrics(CONVERSANT, ACCOUNT_ID, 1234L, false, "banner");

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
        metrics.updateAdapterRequestErrorMetric(RUBICON, MetricName.BADINPUT);
        metrics.updateAdapterRequestErrorMetric(CONVERSANT, MetricName.BADINPUT);
        metrics.updateAdapterRequestErrorMetric(CONVERSANT, MetricName.BADINPUT);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.badinput").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.requests.badinput").getCount()).isEqualTo(2);
    }

    @Test
    public void updateSizeValidationMetricsShouldIncrementMetrics() {
        // when
        metrics.updateSizeValidationMetrics(RUBICON, ACCOUNT_ID, MetricName.ERR);
        metrics.updateSizeValidationMetrics(CONVERSANT, ACCOUNT_ID, MetricName.ERR);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.response.validation.size.err").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.conversant.response.validation.size.err").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.response.validation.size.err").getCount()).isEqualTo(2);
    }

    @Test
    public void updateSecureValidationMetricsShouldIncrementMetrics() {
        // when
        metrics.updateSecureValidationMetrics(RUBICON, ACCOUNT_ID, MetricName.ERR);
        metrics.updateSecureValidationMetrics(CONVERSANT, ACCOUNT_ID, MetricName.ERR);

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
        metrics.updateUserSyncSetsMetric(RUBICON);

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
    public void updateCookieSyncGenMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncGenMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("cookie_sync.rubicon.gen").getCount()).isOne();
    }

    @Test
    public void updateCookieSyncMatchesMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncMatchesMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("cookie_sync.rubicon.matches").getCount()).isOne();
    }

    @Test
    public void updateGpRequestMetricShouldIncrementPlannerRequestAndPlannerSuccessfulRequest() {
        // when
        metrics.updatePlannerRequestMetric(true);

        // then
        assertThat(metricRegistry.counter("pg.planner_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("pg.planner_request_successful").getCount()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementPlannerRequestAndPlannerFailedRequest() {
        // when
        metrics.updatePlannerRequestMetric(false);

        // then
        assertThat(metricRegistry.counter("pg.planner_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("pg.planner_request_failed").getCount()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementUserDetailsSuccessfulRequest() {
        // when
        metrics.updateUserDetailsRequestMetric(true);

        // then
        assertThat(metricRegistry.counter("user_details_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("user_details_request_successful").getCount()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementUserDetailsFailedRequest() {
        // when
        metrics.updateUserDetailsRequestMetric(false);

        // then
        assertThat(metricRegistry.counter("user_details_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("user_details_request_failed").getCount()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementWinSuccessfulRequest() {
        // when
        metrics.updateWinEventRequestMetric(true);

        // then
        assertThat(metricRegistry.counter("win_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("win_request_successful").getCount()).isEqualTo(1);
    }

    @Test
    public void updateGpRequestMetricShouldIncrementWinFailedRequest() {
        // when
        metrics.updateWinEventRequestMetric(false);

        // then
        assertThat(metricRegistry.counter("win_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("win_request_failed").getCount()).isEqualTo(1);
    }

    @Test
    public void updateWinRequestTimeShouldLogTime() {
        // when
        metrics.updateWinRequestTime(20L);

        // then
        assertThat(metricRegistry.timer("win_request_time").getCount()).isEqualTo(1);
    }

    @Test
    public void updateWinRequestPreparationFailedShouldIncrementMetric() {
        // when
        metrics.updateWinRequestPreparationFailed();

        // then
        assertThat(metricRegistry.counter("win_request_preparation_failed").getCount()).isEqualTo(1);
    }

    @Test
    public void updateUserDetailsRequestPreparationFailedShouldIncrementMetric() {
        // when
        metrics.updateUserDetailsRequestPreparationFailed();

        // then
        assertThat(metricRegistry.counter("user_details_request_preparation_failed").getCount()).isEqualTo(1);
    }

    @Test
    public void updateDeliveryRequestMetricShouldIncrementDeliveryRequestAndSuccessfulDeliveryRequest() {
        // when
        metrics.updateDeliveryRequestMetric(true);

        // then
        assertThat(metricRegistry.counter("pg.delivery_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("pg.delivery_request_successful").getCount()).isEqualTo(1);
    }

    @Test
    public void updateDeliveryRequestMetricShouldIncrementDeliveryRequestAndFailedDeliveryRequest() {
        // when
        metrics.updateDeliveryRequestMetric(false);

        // then
        assertThat(metricRegistry.counter("pg.delivery_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("pg.delivery_request_failed").getCount()).isEqualTo(1);
    }

    @Test
    public void updateLineItemsNumberMetricShouldIncrementLineItemsNumberForAAcountValue() {
        // when
        metrics.updateLineItemsNumberMetric(20L);

        // then
        assertThat(metricRegistry.counter("pg.planner_lineitems_received").getCount()).isEqualTo(20);
    }

    @Test
    public void updatePlannerRequestTimeShouldLogTime() {
        // when
        metrics.updatePlannerRequestTime(20L);

        // then
        assertThat(metricRegistry.timer("pg.planner_request_time").getCount()).isEqualTo(1);
    }

    @Test
    public void updateDeliveryRequestTimeShouldLogTime() {
        // when
        metrics.updateDeliveryRequestTime(20L);

        // then
        assertThat(metricRegistry.timer("pg.delivery_request_time").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAuctionTcfMetricsShouldIncrementMetrics() {
        // when
        metrics.updateAuctionTcfMetrics(RUBICON, MetricName.OPENRTB2_WEB, true, true, true, true);
        metrics.updateAuctionTcfMetrics(CONVERSANT, MetricName.OPENRTB2_WEB, false, true, true, false);
        metrics.updateAuctionTcfMetrics(CONVERSANT, MetricName.OPENRTB2_APP, true, false, false, true);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.userid_removed").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.geo_masked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.analytics_blocked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.request_blocked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.openrtb2-web.tcf.geo_masked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.openrtb2-web.tcf.analytics_blocked").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.openrtb2-app.tcf.userid_removed").getCount()).isOne();
        assertThat(metricRegistry.counter("adapter.conversant.openrtb2-app.tcf.request_blocked").getCount()).isOne();
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
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.NONE);

        // when
        metrics.updateAccountRequestMetrics(ACCOUNT_ID, MetricName.OPENRTB2_WEB);
        metrics.updateAdapterResponseTime(RUBICON, ACCOUNT_ID, 500);
        metrics.updateAdapterRequestNobidMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, true, "banner");

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
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.BASIC);

        // when
        metrics.updateAccountRequestMetrics(ACCOUNT_ID, MetricName.OPENRTB2_WEB);
        metrics.updateAdapterResponseTime(RUBICON, ACCOUNT_ID, 500);
        metrics.updateAdapterRequestNobidMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, true, "banner");

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
        metrics.updateCacheCreativeSize("accountId", 123, MetricName.JSON);
        metrics.updateCacheCreativeSize("accountId", 456, MetricName.XML);
        metrics.updateCacheCreativeSize("accountId", 789, MetricName.UNKNOWN);

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
        metrics.updateSettingsCacheRefreshTime(MetricName.STORED_REQUEST, MetricName.INITIALIZE, 123L);

        // then
        assertThat(metricRegistry
                .timer("settings.cache.stored-request.refresh.initialize.db_query_time")
                .getCount())
                .isEqualTo(1);
    }

    @Test
    public void updateSettingsCacheRefreshErrorMetricShouldIncrementMetric() {
        // when
        metrics.updateSettingsCacheRefreshErrorMetric(MetricName.STORED_REQUEST, MetricName.INITIALIZE);

        // then
        assertThat(metricRegistry.counter("settings.cache.stored-request.refresh.initialize.err").getCount())
                .isEqualTo(1);
    }

    @Test
    public void updateSettingsCacheEventMetricShouldIncrementMetric() {
        // when
        metrics.updateSettingsCacheEventMetric(MetricName.ACCOUNT, MetricName.HIT);

        // then
        assertThat(metricRegistry.counter("settings.cache.account.hit").getCount()).isEqualTo(1);
    }

    @Test
    public void updateHooksMetricsShouldIncrementMetrics() {
        // when
        metrics.updateHooksMetrics(
                "module1", Stage.ENTRYPOINT, "hook1", ExecutionStatus.SUCCESS, 5L, ExecutionAction.UPDATE);
        metrics.updateHooksMetrics(
                "module1", Stage.RAW_AUCTION_REQUEST, "hook2", ExecutionStatus.SUCCESS, 5L, ExecutionAction.NO_ACTION);
        metrics.updateHooksMetrics(
                "module1",
                Stage.PROCESSED_AUCTION_REQUEST,
                "hook3",
                ExecutionStatus.SUCCESS,
                5L,
                ExecutionAction.REJECT);
        metrics.updateHooksMetrics(
                "module2", Stage.BIDDER_REQUEST, "hook1", ExecutionStatus.FAILURE, 6L, null);
        metrics.updateHooksMetrics(
                "module2", Stage.RAW_BIDDER_RESPONSE, "hook2", ExecutionStatus.TIMEOUT, 7L, null);
        metrics.updateHooksMetrics(
                "module2", Stage.PROCESSED_BIDDER_RESPONSE, "hook3", ExecutionStatus.EXECUTION_FAILURE, 5L, null);
        metrics.updateHooksMetrics(
                "module2", Stage.AUCTION_RESPONSE, "hook4", ExecutionStatus.INVOCATION_FAILURE, 5L, null);

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
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.DETAILED);

        // when
        metrics.updateAccountHooksMetrics(
                "accountId", "module1", ExecutionStatus.SUCCESS, ExecutionAction.UPDATE);
        metrics.updateAccountHooksMetrics(
                "accountId", "module2", ExecutionStatus.FAILURE, null);
        metrics.updateAccountHooksMetrics(
                "accountId", "module3", ExecutionStatus.TIMEOUT, null);

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
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.BASIC);

        // when
        metrics.updateAccountHooksMetrics(
                "accountId", "module1", ExecutionStatus.SUCCESS, ExecutionAction.UPDATE);

        // then
        assertThat(metricRegistry.counter("account.accountId.modules.module.module1.call").getCount())
                .isZero();
        assertThat(metricRegistry.counter("account.accountId.modules.module.module1.success.update").getCount())
                .isZero();
    }

    @Test
    public void updateAccountModuleDurationMetricShouldIncrementMetricsIfVerbosityIsDetailed() {
        // given
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.DETAILED);

        // when
        metrics.updateAccountModuleDurationMetric(
                "accountId", "module1", 5L);
        metrics.updateAccountModuleDurationMetric(
                "accountId", "module2", 6L);

        // then
        assertThat(metricRegistry.timer("account.accountId.modules.module.module1.duration").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("account.accountId.modules.module.module2.duration").getCount())
                .isEqualTo(1);
    }

    @Test
    public void updateAccountModuleDurationMetricShouldNotIncrementMetricsIfVerbosityIsNotAtLeastDetailed() {
        // given
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.BASIC);

        // when
        metrics.updateAccountModuleDurationMetric(
                "accountId", "module1", 5L);

        // then
        assertThat(metricRegistry.timer("account.accountId.modules.module.module1.duration").getCount())
                .isZero();
    }

    @Test
    public void shouldIncrementWinNotificationMetric() {
        // when
        metrics.updateWinNotificationMetric();

        // then
        assertThat(metricRegistry.counter("win_notifications").getCount()).isEqualTo(1);
    }

    private void verifyCreatesConfiguredCounterType(Consumer<Metrics> metricsConsumer) {
        final EnumMap<CounterType, Class<? extends Metric>> counterTypeClasses = new EnumMap<>(CounterType.class);
        counterTypeClasses.put(CounterType.COUNTER, Counter.class);
        counterTypeClasses.put(CounterType.FLUSHING_COUNTER, ResettingCounter.class);
        counterTypeClasses.put(CounterType.METER, Meter.class);

        final SoftAssertions softly = new SoftAssertions();

        for (CounterType counterType : CounterType.values()) {
            // given
            metricRegistry = new MetricRegistry();

            // when
            metricsConsumer.accept(new Metrics(metricRegistry, CounterType.valueOf(counterType.name()),
                    accountMetricsVerbosity));

            // then
            softly.assertThat(metricRegistry.getMetrics()).hasValueSatisfying(new Condition<>(
                    metric -> metric.getClass() == counterTypeClasses.get(counterType),
                    null));
        }

        softly.assertAll();
    }
}
