package org.prebid.server.handler;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.metric.CookieSyncMetrics;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class SetuidHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String ADNXS = "adnxs";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private AnalyticsReporter analyticsReporter;
    @Mock
    private Metrics metrics;
    @Mock
    private CookieSyncMetrics cookieSyncMetrics;
    @Mock
    private CookieSyncMetrics.BidderCookieSyncMetrics bidderCookieSyncMetrics;

    private SetuidHandler setuidHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(routingContext.addCookie(any())).willReturn(routingContext);

        given(metrics.cookieSync()).willReturn(cookieSyncMetrics);
        given(cookieSyncMetrics.forBidder(anyString())).willReturn(bidderCookieSyncMetrics);

        setuidHandler = new SetuidHandler(uidsCookieService, analyticsReporter, metrics);
    }

    @Test
    public void shouldRespondWithErrorIfOptedOut() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build()));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse);
    }

    @Test
    public void shouldRespondWithErrorIfBidderParamIsMissing() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build()));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse);
    }

    @Test
    public void shouldRemoveUidFromCookieIfMissingInRequest() {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        uids.put(ADNXS, UidWithExpiry.live("12345"));
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder().uids(uids).build()));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);

        // this uids cookie stands for {"tempUIDs":{"adnxs":{"uid":"12345"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJhZG54cyI6eyJ1aWQiOiIxMjM0NSJ9fX0="));

        // when
        setuidHandler.handle(routingContext);

        // then
        final Cookie uidsCookie = captureCookie();
        verify(httpResponse).end();
        // this uids cookie value stands for {"uids":{"adnxs":"12345"}}
        final Uids decodedUids = decodeUids(uidsCookie.getValue());
        assertThat(decodedUids.getUids()).hasSize(1);
        assertThat(decodedUids.getUids().get(ADNXS).getUid()).isEqualTo("12345");
    }

    @Test
    public void shouldIgnoreFacebookSentinel() {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap("audienceNetwork", UidWithExpiry.live("facebookUid"))).build()));

        given(httpRequest.getParam("bidder")).willReturn("audienceNetwork");
        given(httpRequest.getParam("uid")).willReturn("0");

        // this uids cookie value stands for {"tempUIDs":{"audienceNetwork":{"uid":"facebookUid"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJhdWRpZW5jZU5ldHdvcmsiOnsidWlkIjoiZmFjZWJvb2tVaWQifX19"));

        // when
        setuidHandler.handle(routingContext);

        // then
        final Cookie uidsCookie = captureCookie();
        verify(httpResponse).end();
        // this uids cookie value stands for {"uids":{"audienceNetwork":"facebookUid"}}
        final Uids decodedUids = decodeUids(uidsCookie.getValue());
        assertThat(decodedUids.getUids()).hasSize(1);
        assertThat(decodedUids.getUids().get("audienceNetwork").getUid()).isEqualTo("facebookUid");
    }

    @Test
    public void shouldUpdateUidInCookieWithRequestValue() {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        uids.put(ADNXS, UidWithExpiry.live("12345"));
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder().uids(uids).build()));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("updatedUid");

        // {"tempUIDs":{"adnxs":{"uid":"12345"}, "rubicon":{"uid":"updatedUid"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJhZG54cyI6eyJ1aWQiOiIxMjM0NSJ9LCAicnViaWNvbiI6eyJ1aWQiOiJ1cGRhdGVkVW"
                        + "lkIn19fQ=="));

        // when
        setuidHandler.handle(routingContext);

        // then
        final Cookie uidsCookie = captureCookie();
        verify(httpResponse).end();
        verify(cookieSyncMetrics).forBidder(eq(RUBICON));
        verify(bidderCookieSyncMetrics).incCounter(eq(MetricName.sets));
        // this uids cookie value stands for {"uids":{"adnxs":"12345","rubicon":"updatedUid"}}
        final Uids decodedUids = decodeUids(uidsCookie.getValue());
        assertThat(decodedUids.getUids()).hasSize(2);
        assertThat(decodedUids.getUids().get(RUBICON).getUid()).isEqualTo("updatedUid");
        assertThat(decodedUids.getUids().get(ADNXS).getUid()).isEqualTo("12345");
    }

    @Test
    public void shouldUpdateOptOutsMetricIfOptedOut() {
        // given
        // this uids cookie value stands for {"optout": true}
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build()));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(cookieSyncMetrics).incCounter(eq(MetricName.opt_outs));
    }

    @Test
    public void shouldUpdateBadRequestsMetricIfBidderParamIsMissing() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build()));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(cookieSyncMetrics).incCounter(eq(MetricName.bad_requests));
    }

    @Test
    public void shouldUpdateSetsMetric() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build()));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("updatedUid");

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(bidderCookieSyncMetrics).incCounter(eq(MetricName.sets));
    }

    @Test
    public void shouldPassUnauthorizedEventToAnalyticsReporterIfOptedOut() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build()));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        setuidHandler.handle(routingContext);

        // then
        final SetuidEvent setuidEvent = captureSetuidEvent();
        assertThat(setuidEvent).isEqualTo(SetuidEvent.builder().status(401).build());
    }

    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfBidderParamIsMissing() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build()));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        setuidHandler.handle(routingContext);

        // then
        final SetuidEvent setuidEvent = captureSetuidEvent();
        assertThat(setuidEvent).isEqualTo(SetuidEvent.builder().status(400).build());
    }

    @Test
    public void shouldPassUnsuccessfulEventToAnalyticsReporterIfUidMissingInRequest() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build()));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);

        // when
        setuidHandler.handle(routingContext);

        // then
        final SetuidEvent setuidEvent = captureSetuidEvent();
        assertThat(setuidEvent).isEqualTo(SetuidEvent.builder()
                .status(200)
                .bidder(RUBICON)
                .success(false)
                .build());
    }

    @Test
    public void shouldPassUnsuccessfulEventToAnalyticsReporterIfFacebookSentinel() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build()));

        given(httpRequest.getParam("bidder")).willReturn("audienceNetwork");
        given(httpRequest.getParam("uid")).willReturn("0");


        // when
        setuidHandler.handle(routingContext);

        // then
        final SetuidEvent setuidEvent = captureSetuidEvent();
        assertThat(setuidEvent).isEqualTo(SetuidEvent.builder()
                .status(200)
                .bidder("audienceNetwork")
                .uid("0")
                .success(false)
                .build());
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build()));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("updatedUid");

        // when
        setuidHandler.handle(routingContext);

        // then
        final SetuidEvent setuidEvent = captureSetuidEvent();
        assertThat(setuidEvent).isEqualTo(SetuidEvent.builder()
                .status(200)
                .bidder(RUBICON)
                .uid("updatedUid")
                .success(true)
                .build());
    }

    private Cookie captureCookie() {
        final ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(routingContext).addCookie(cookieCaptor.capture());
        return cookieCaptor.getValue();
    }

    private static Uids decodeUids(String value) {
        return Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(value)), Uids.class);
    }

    private SetuidEvent captureSetuidEvent() {
        final ArgumentCaptor<SetuidEvent> setuidEventCaptor = ArgumentCaptor.forClass(SetuidEvent.class);
        verify(analyticsReporter).processEvent(setuidEventCaptor.capture());
        return setuidEventCaptor.getValue();
    }
}
