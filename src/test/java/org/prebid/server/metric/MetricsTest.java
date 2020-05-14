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
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class MetricsTest {

    private static final String RUBICON = "rubicon";
    private static final String INVALID_BIDDER = "invalid";
    private static final String ACCOUNT_ID = "accountId";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private MetricRegistry metricRegistry;
    @Mock
    private AccountMetricsVerbosity accountMetricsVerbosity;
    @Mock
    private BidderCatalog bidderCatalog;

    private Metrics metrics;

    @Before
    public void setUp() {
        metricRegistry = new MetricRegistry();
        given(accountMetricsVerbosity.forAccount(anyString())).willReturn(AccountMetricsVerbosityLevel.detailed);
        given(bidderCatalog.isValidName(any())).willReturn(true);

        metrics = new Metrics(metricRegistry, CounterType.counter, accountMetricsVerbosity, bidderCatalog);
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
        assertThat(metricRegistry.counter("account.accountId.requests.type.openrtb2-web").getCount()).isEqualTo(1);
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
        assertThat(metricRegistry.counter("usersync.opt_outs").getCount()).isEqualTo(1);
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
        assertThat(metricRegistry.counter("usersync.rubicon.sets").getCount()).isEqualTo(1);
    }

    @Test
    public void cookieSyncShouldReturnSameCookieSyncMetricsOnSuccessiveCalls() {
        assertThat(metrics.cookieSync()).isSameAs(metrics.cookieSync());
    }

    @Test
    public void cookieSyncShouldReturnCookieSyncMetricsConfiguredWithCounterType() {
        verifyCreatesConfiguredCounterType(
                metrics -> metrics.cookieSync().incCounter(MetricName.gen));
    }

    @Test
    public void cookieSyncShouldReturnCookieSyncMetricsConfiguredWithPrefix() {
        // when
        metrics.cookieSync().incCounter(MetricName.gen);

        // then
        assertThat(metricRegistry.counter("cookie_sync.gen").getCount()).isEqualTo(1);
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
                .incCounter(MetricName.gen));
    }

    @Test
    public void shouldReturnBidderCookieSyncMetricsConfiguredWithBidder() {
        // when
        metrics.cookieSync().forBidder(RUBICON).incCounter(MetricName.gen);

        // then
        assertThat(metricRegistry.counter("cookie_sync.rubicon.gen").getCount()).isEqualTo(1);
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
        assertThat(metricRegistry.counter("imps_native").getCount()).isEqualTo(1);
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

        assertThat(metricRegistry.counter("imps_banner").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("imps_video").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("imps_native").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("imps_audio").getCount()).isEqualTo(2);
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
        metrics.updateRequestTypeMetric(MetricName.openrtb2web, MetricName.blacklisted_account);
        metrics.updateRequestTypeMetric(MetricName.openrtb2app, MetricName.blacklisted_app);
        metrics.updateRequestTypeMetric(MetricName.openrtb2app, MetricName.err);
        metrics.updateRequestTypeMetric(MetricName.amp, MetricName.badinput);
        metrics.updateRequestTypeMetric(MetricName.amp, MetricName.networkerr);

        // then
        assertThat(metricRegistry.counter("requests.ok.openrtb2-web").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("requests.blacklisted_account.openrtb2-web").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("requests.blacklisted_app.openrtb2-app").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("requests.err.openrtb2-app").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("requests.badinput.amp").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("requests.networkerr.amp").getCount()).isEqualTo(1);
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
    public void updateAdapterRequestTypeAndNoCookieMetricsShouldUpdateMetricsAsExpected() {
        // given
        given(bidderCatalog.isValidName(INVALID_BIDDER)).willReturn(false);
        given(bidderCatalog.nameByAlias(INVALID_BIDDER)).willReturn(RUBICON, null);

        // when
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(RUBICON, MetricName.openrtb2app, true);
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(RUBICON, MetricName.amp, false);
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(INVALID_BIDDER, MetricName.openrtb2app, false);
        metrics.updateAdapterRequestTypeAndNoCookieMetrics(INVALID_BIDDER, MetricName.amp, false);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.openrtb2-app").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.rubicon.requests.type.amp").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.UNKNOWN.requests.type.amp").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.no_cookie_requests").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterResponseTimeShouldUpdateMetrics() {
        // given
        given(bidderCatalog.isValidName(INVALID_BIDDER)).willReturn(false);
        given(bidderCatalog.nameByAlias(INVALID_BIDDER)).willReturn(RUBICON, null);

        // when
        metrics.updateAdapterResponseTime(RUBICON, ACCOUNT_ID, 500);
        metrics.updateAdapterResponseTime(INVALID_BIDDER, ACCOUNT_ID, 500);
        metrics.updateAdapterResponseTime(INVALID_BIDDER, ACCOUNT_ID, 500);

        // then
        assertThat(metricRegistry.timer("adapter.rubicon.request_time").getCount()).isEqualTo(2);
        assertThat(metricRegistry.timer("account.accountId.rubicon.request_time").getCount()).isEqualTo(2);
        assertThat(metricRegistry.timer("adapter.UNKNOWN.request_time").getCount()).isEqualTo(1);
        assertThat(metricRegistry.timer("account.accountId.UNKNOWN.request_time").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterRequestNobidMetricsShouldIncrementMetrics() {
        // given
        given(bidderCatalog.isValidName(INVALID_BIDDER)).willReturn(false);
        given(bidderCatalog.nameByAlias(INVALID_BIDDER)).willReturn(RUBICON, null);

        // when
        metrics.updateAdapterRequestNobidMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterRequestNobidMetrics(INVALID_BIDDER, ACCOUNT_ID);
        metrics.updateAdapterRequestNobidMetrics(INVALID_BIDDER, ACCOUNT_ID);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.nobid").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.nobid").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.UNKNOWN.requests.nobid").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.UNKNOWN.requests.nobid").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterRequestGotbidsMetricsShouldIncrementMetrics() {
        // given
        given(bidderCatalog.isValidName(INVALID_BIDDER)).willReturn(false);
        given(bidderCatalog.nameByAlias(INVALID_BIDDER)).willReturn(RUBICON, null);

        // when
        metrics.updateAdapterRequestGotbidsMetrics(RUBICON, ACCOUNT_ID);
        metrics.updateAdapterRequestGotbidsMetrics(INVALID_BIDDER, ACCOUNT_ID);
        metrics.updateAdapterRequestGotbidsMetrics(INVALID_BIDDER, ACCOUNT_ID);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.gotbids").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("account.accountId.rubicon.requests.gotbids").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.UNKNOWN.requests.gotbids").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.UNKNOWN.requests.gotbids").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterBidMetricsShouldUpdateMetrics() {
        // given
        given(bidderCatalog.isValidName(INVALID_BIDDER)).willReturn(false);
        given(bidderCatalog.nameByAlias(INVALID_BIDDER)).willReturn(RUBICON, null);

        // when
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, true, "banner");
        metrics.updateAdapterBidMetrics(RUBICON, ACCOUNT_ID, 1234L, false, "video");
        metrics.updateAdapterBidMetrics(INVALID_BIDDER, ACCOUNT_ID, 1234L, false, "banner");
        metrics.updateAdapterBidMetrics(INVALID_BIDDER, ACCOUNT_ID, 1234L, false, "banner");

        // then
        assertThat(metricRegistry.histogram("adapter.rubicon.prices").getCount()).isEqualTo(3);
        assertThat(metricRegistry.histogram("account.accountId.rubicon.prices").getCount()).isEqualTo(3);
        assertThat(metricRegistry.counter("adapter.rubicon.bids_received").getCount()).isEqualTo(3);
        assertThat(metricRegistry.counter("account.accountId.rubicon.bids_received").getCount()).isEqualTo(3);
        assertThat(metricRegistry.counter("adapter.rubicon.banner.adm_bids_received").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.video.nurl_bids_received").getCount()).isEqualTo(1);
        assertThat(metricRegistry.histogram("adapter.UNKNOWN.prices").getCount()).isEqualTo(1);
        assertThat(metricRegistry.histogram("account.accountId.UNKNOWN.prices").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.UNKNOWN.bids_received").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("account.accountId.UNKNOWN.bids_received").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.UNKNOWN.banner.nurl_bids_received").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAdapterRequestErrorMetricShouldIncrementMetrics() {
        // given
        given(bidderCatalog.isValidName(INVALID_BIDDER)).willReturn(false);
        given(bidderCatalog.nameByAlias(INVALID_BIDDER)).willReturn(RUBICON, null);

        // when
        metrics.updateAdapterRequestErrorMetric(RUBICON, MetricName.badinput);
        metrics.updateAdapterRequestErrorMetric(INVALID_BIDDER, MetricName.badinput);
        metrics.updateAdapterRequestErrorMetric(INVALID_BIDDER, MetricName.badinput);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.requests.badinput").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("adapter.UNKNOWN.requests.badinput").getCount()).isEqualTo(1);
    }

    @Test
    public void updateCookieSyncRequestMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncRequestMetric();

        // then
        assertThat(metricRegistry.counter("cookie_sync_requests").getCount()).isEqualTo(1);
    }

    @Test
    public void updateUserSyncOptoutMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncOptoutMetric();

        // then
        assertThat(metricRegistry.counter("usersync.opt_outs").getCount()).isEqualTo(1);
    }

    @Test
    public void updateUserSyncBadRequestMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncBadRequestMetric();

        // then
        assertThat(metricRegistry.counter("usersync.bad_requests").getCount()).isEqualTo(1);
    }

    @Test
    public void updateUserSyncSetsMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncSetsMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("usersync.rubicon.sets").getCount()).isEqualTo(1);
    }

    @Test
    public void updateUserSyncTcfBlockedMetricShouldIncrementMetric() {
        // when
        metrics.updateUserSyncTcfBlockedMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("usersync.rubicon.tcf.blocked").getCount()).isEqualTo(1);
    }

    @Test
    public void updateCookieSyncTcfBlockedMetricShouldIncrementMetric() {
        // given
        given(bidderCatalog.isValidName(INVALID_BIDDER)).willReturn(false);
        given(bidderCatalog.nameByAlias(INVALID_BIDDER)).willReturn(RUBICON, null);

        // when
        metrics.updateCookieSyncTcfBlockedMetric(RUBICON);
        metrics.updateCookieSyncTcfBlockedMetric(INVALID_BIDDER);
        metrics.updateCookieSyncTcfBlockedMetric(INVALID_BIDDER);

        // then
        assertThat(metricRegistry.counter("cookie_sync.rubicon.tcf.blocked").getCount()).isEqualTo(2);
        assertThat(metricRegistry.counter("cookie_sync.UNKNOWN.tcf.blocked").getCount()).isEqualTo(1);
    }

    @Test
    public void updateCookieSyncGenMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncGenMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("cookie_sync.rubicon.gen").getCount()).isEqualTo(1);
    }

    @Test
    public void updateCookieSyncMatchesMetricShouldIncrementMetric() {
        // when
        metrics.updateCookieSyncMatchesMetric(RUBICON);

        // then
        assertThat(metricRegistry.counter("cookie_sync.rubicon.matches").getCount()).isEqualTo(1);
    }

    @Test
    public void updateAuctionTcfMetricShouldIncrementMetrics() {
        // when
        metrics.updateAuctionTcfMetrics(RUBICON, MetricName.openrtb2web, true, true, true, true);

        // then
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.userid_removed").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.geo_masked").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.request_blocked").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("adapter.rubicon.openrtb2-web.tcf.analytics_blocked").getCount())
                .isEqualTo(1);
    }

    @Test
    public void updatePrivacyCoppaMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyCoppaMetric();

        // then
        assertThat(metricRegistry.counter("privacy.coppa").getCount()).isEqualTo(1);
    }

    @Test
    public void updatePrivacyLmtMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyLmtMetric();

        // then
        assertThat(metricRegistry.counter("privacy.lmt").getCount()).isEqualTo(1);
    }

    @Test
    public void updatePrivacyCcpaMetricsShouldIncrementMetrics() {
        // when
        metrics.updatePrivacyCcpaMetrics(true, true);

        // then
        assertThat(metricRegistry.counter("privacy.usp.specified").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("privacy.usp.opt-out").getCount()).isEqualTo(1);
    }

    @Test
    public void updatePrivacyTcfInvalidMetricShouldIncrementMetric() {
        // when
        metrics.updatePrivacyTcfInvalidMetric();

        // then
        assertThat(metricRegistry.counter("privacy.tcf.invalid").getCount()).isEqualTo(1);
    }

    @Test
    public void updatePrivacyTcfGeoMetricShouldIncrementMetrics() {
        // when
        metrics.updatePrivacyTcfGeoMetric(1, null);
        metrics.updatePrivacyTcfGeoMetric(2, true);
        metrics.updatePrivacyTcfGeoMetric(2, false);

        // then
        assertThat(metricRegistry.counter("privacy.tcf.v1.unknown-geo").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("privacy.tcf.v2.in-geo").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("privacy.tcf.v2.out-geo").getCount()).isEqualTo(1);
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

    @Test
    public void shouldIncrementConnectionAcceptErrorsMetric() {
        // when
        metrics.updateConnectionAcceptErrors();

        // then
        assertThat(metricRegistry.counter("connection_accept_errors").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldUpdateDatabaseQueryTimeMetric() {
        // when
        metrics.updateDatabaseQueryTimeMetric(456L);

        // then
        assertThat(metricRegistry.timer("db_query_time").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementDatabaseCircuitBreakerOpenMetric() {
        // when
        metrics.updateDatabaseCircuitBreakerMetric(true);

        // then
        assertThat(metricRegistry.counter("db_circuitbreaker_opened").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementDatabaseCircuitBreakerCloseMetric() {
        // when
        metrics.updateDatabaseCircuitBreakerMetric(false);

        // then
        assertThat(metricRegistry.counter("db_circuitbreaker_closed").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementHttpClientCircuitBreakerOpenMetric() {
        // when
        metrics.updateHttpClientCircuitBreakerMetric(true);

        // then
        assertThat(metricRegistry.counter("httpclient_circuitbreaker_opened").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementHttpClientCircuitBreakerCloseMetric() {
        // when
        metrics.updateHttpClientCircuitBreakerMetric(false);

        // then
        assertThat(metricRegistry.counter("httpclient_circuitbreaker_closed").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementGeoLocationCircuitBreakerOpenMetric() {
        // when
        metrics.updateGeoLocationCircuitBreakerMetric(true);

        // then
        assertThat(metricRegistry.counter("geolocation_circuitbreaker_opened").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementGeoLocationCircuitBreakerCloseMetric() {
        // when
        metrics.updateGeoLocationCircuitBreakerMetric(false);

        // then
        assertThat(metricRegistry.counter("geolocation_circuitbreaker_closed").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementBothGeoLocationRequestsAndSuccessfulMetrics() {
        // when
        metrics.updateGeoLocationMetric(true);

        // then
        assertThat(metricRegistry.counter("geolocation_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("geolocation_successful").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementBothGeoLocationRequestsAndFailMetrics() {
        // when
        metrics.updateGeoLocationMetric(false);

        // then
        assertThat(metricRegistry.counter("geolocation_requests").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("geolocation_fail").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldAlwaysIncrementGeoLocationRequestsMetricAndEitherSuccessfulOrFailMetricDependingOnFlag() {
        // when
        metrics.updateGeoLocationMetric(true);
        metrics.updateGeoLocationMetric(false);
        metrics.updateGeoLocationMetric(true);

        // then
        assertThat(metricRegistry.counter("geolocation_requests").getCount()).isEqualTo(3);
        assertThat(metricRegistry.counter("geolocation_fail").getCount()).isEqualTo(1);
        assertThat(metricRegistry.counter("geolocation_successful").getCount()).isEqualTo(2);
    }

    @Test
    public void shouldIncrementStoredRequestFoundMetric() {
        // when
        metrics.updateStoredRequestMetric(true);

        // then
        assertThat(metricRegistry.counter("stored_requests_found").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementStoredRequestMissingMetric() {
        // when
        metrics.updateStoredRequestMetric(false);

        // then
        assertThat(metricRegistry.counter("stored_requests_missing").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementStoredImpFoundMetric() {
        // when
        metrics.updateStoredImpsMetric(true);

        // then
        assertThat(metricRegistry.counter("stored_imps_found").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementStoredImpMissingMetric() {
        // when
        metrics.updateStoredImpsMetric(false);

        // then
        assertThat(metricRegistry.counter("stored_imps_missing").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementPrebidCacheRequestSuccessTimer() {
        // when
        metrics.updateCacheRequestSuccessTime(1424L);

        // then
        assertThat(metricRegistry.timer("prebid_cache_request_success_time").getCount()).isEqualTo(1);
    }

    @Test
    public void shouldIncrementPrebidCacheRequestFailedTimer() {
        // when
        metrics.updateCacheRequestFailedTime(1424L);

        // then
        assertThat(metricRegistry.timer("prebid_cache_request_error_time").getCount()).isEqualTo(1);
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
                    accountMetricsVerbosity, bidderCatalog));

            // then
            softly.assertThat(metricRegistry.getMetrics()).hasValueSatisfying(new Condition<>(
                    metric -> metric.getClass() == counterTypeClasses.get(counterType),
                    null));
        }

        softly.assertAll();
    }
}
