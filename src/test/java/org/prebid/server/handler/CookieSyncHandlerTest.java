package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.util.AsciiString;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
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
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.appnexus.AppnexusUsersyncer;
import org.prebid.server.bidder.rubicon.RubiconUsersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class CookieSyncHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";

    private static final String APPNEXUS = "appnexus";
    private static final String APPNEXUS_COOKIE = "adnxs";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private AnalyticsReporter analyticsReporter;
    @Mock
    private Metrics metrics;

    private CookieSyncHandler cookieSyncHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private RubiconUsersyncer rubiconUsersyncer;
    @Mock
    private AppnexusUsersyncer appnexusUsersyncer;

    @Before
    public void setUp() {
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build()));

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);

        cookieSyncHandler = new CookieSyncHandler(uidsCookieService, bidderCatalog, analyticsReporter, metrics);
    }

    @Test
    public void shouldRespondWithErrorIfOptedOut() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build()));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).setStatusMessage(eq("User has opted out"));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse, bidderCatalog);
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse, bidderCatalog);
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{"));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).setStatusMessage(eq("JSON parse failed"));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse, bidderCatalog);
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(CookieSyncRequest.of(emptyList())));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse)
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithSomeBidderStatusesIfSomeUidsMissingInCookies() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build()));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS))));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        givenUsersyncersReturningFamilyName();

        final UsersyncInfo appnexusUsersyncInfo = UsersyncInfo.of("http://adnxsexample.com", "redirect", false);
        given(bidderCatalog.usersyncerByName(APPNEXUS).usersyncInfo()).willReturn(appnexusUsersyncInfo);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("ok",
                singletonList(BidderUsersyncStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(appnexusUsersyncInfo)
                        .build())));
    }

    @Test
    public void shouldRespondWithBidderStatusForAllBiddersIfBiddersListOmittedInRequest() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(CookieSyncRequest.of(null)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        givenUsersyncersReturningFamilyName();
        final UsersyncInfo appnexusUsersyncInfo = UsersyncInfo.of("http://adnxsexample.com", "redirect", false);
        final UsersyncInfo rubiconUsersyncInfo = UsersyncInfo.of("http://rubiconexample.com", "redirect", false);

        given(appnexusUsersyncer.usersyncInfo()).willReturn(appnexusUsersyncInfo);
        given(rubiconUsersyncer.usersyncInfo()).willReturn(rubiconUsersyncInfo);

        given(bidderCatalog.names()).willReturn(new HashSet<>(asList(APPNEXUS, RUBICON)));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(appnexusUsersyncInfo).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(rubiconUsersyncInfo).build())));
    }

    @Test
    public void shouldRespondWithNoBidderStatusesIfAllUidsPresentInCookies() throws IOException {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        uids.put(APPNEXUS_COOKIE, UidWithExpiry.live("12345"));
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder().uids(uids).build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS))));

        givenUsersyncersReturningFamilyName();

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("ok", emptyList()));
    }

    @Test
    public void shouldTolerateUnsupportedBidder() throws IOException {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder().uids(uids).build()));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, "unsupported"))));

        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isValidName("unsupported")).willReturn(false);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("ok");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsOnly(tuple("unsupported", "Unsupported bidder"));
    }

    @Test
    public void shouldTolerateDisabledBidder() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build()));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, "disabled"))));

        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isValidName(RUBICON)).willReturn(true);
        given(bidderCatalog.isValidName("disabled")).willReturn(true);

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive("disabled")).willReturn(false);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("ok");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsOnly(tuple("disabled",
                        "disabled is not configured properly on this Prebid Server deploy. "
                                + "If you believe this should work, contact the company hosting the service and tell "
                                + "them to check their configuration."));
    }

    @Test
    public void shouldRespondWithNoCookieStatusIfNoLiveUids() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(APPNEXUS_COOKIE, UidWithExpiry.expired("12345"))).build()));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(singletonList(APPNEXUS))));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        givenUsersyncersReturningFamilyName();

        final UsersyncInfo appnexusUsersyncInfo = UsersyncInfo.of("http://adnxsexample.com", "redirect", false);
        given(bidderCatalog.usersyncerByName(APPNEXUS).usersyncInfo()).willReturn(appnexusUsersyncInfo);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie",
                singletonList(BidderUsersyncStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(appnexusUsersyncInfo)
                        .build())));
    }

    @Test
    public void shouldIncrementMetrics() {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(CookieSyncRequest.of(emptyList())));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.cookie_sync_requests));
    }

    @Test
    public void shouldPassUnauthorizedEventToAnalyticsReporterIfOptedOut() {
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build()));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncEvent cookieSyncEvent = captureCookieSyncEvent();
        assertThat(cookieSyncEvent).isEqualTo(CookieSyncEvent.error(401, "user has opted out"));
    }

    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncEvent cookieSyncEvent = captureCookieSyncEvent();
        assertThat(cookieSyncEvent).isEqualTo(CookieSyncEvent.error(400, "request has no body"));
    }


    @Test
    public void shouldPassBadRequestEventToAnalyticsReporterIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{"));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncEvent cookieSyncEvent = captureCookieSyncEvent();
        assertThat(cookieSyncEvent).isEqualTo(CookieSyncEvent.error(400, "JSON parse failed"));
    }


    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build()));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS))));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        givenUsersyncersReturningFamilyName();

        final UsersyncInfo appnexusUsersyncInfo = UsersyncInfo.of("http://adnxsexample.com", "redirect", false);
        given(bidderCatalog.usersyncerByName(APPNEXUS).usersyncInfo()).willReturn(appnexusUsersyncInfo);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncEvent cookieSyncEvent = captureCookieSyncEvent();
        assertThat(cookieSyncEvent).isEqualTo(CookieSyncEvent.builder()
                .status(200)
                .bidderStatus(singletonList(BidderUsersyncStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of("http://adnxsexample.com", "redirect", false))
                        .build()))
                .build());
    }

    private static Buffer givenRequestBody(CookieSyncRequest request) {
        try {
            return Buffer.buffer(mapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }
    }

    private void givenUsersyncersReturningFamilyName() {
        given(bidderCatalog.usersyncerByName(eq(RUBICON))).willReturn(rubiconUsersyncer);
        given(bidderCatalog.isValidName(eq(RUBICON))).willReturn(true);
        given(bidderCatalog.usersyncerByName(eq(APPNEXUS))).willReturn(appnexusUsersyncer);
        given(bidderCatalog.isValidName(eq(APPNEXUS))).willReturn(true);

        given(rubiconUsersyncer.cookieFamilyName()).willReturn(RUBICON);

        given(appnexusUsersyncer.cookieFamilyName()).willReturn(APPNEXUS_COOKIE);
    }

    private CookieSyncResponse captureCookieSyncResponse() throws IOException {
        final ArgumentCaptor<String> cookieSyncResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).end(cookieSyncResponseCaptor.capture());
        return mapper.readValue(cookieSyncResponseCaptor.getValue(), CookieSyncResponse.class);
    }

    private CookieSyncEvent captureCookieSyncEvent() {
        final ArgumentCaptor<CookieSyncEvent> cookieSyncEventCaptor = ArgumentCaptor.forClass(CookieSyncEvent.class);
        verify(analyticsReporter).processEvent(cookieSyncEventCaptor.capture());
        return cookieSyncEventCaptor.getValue();
    }
}
