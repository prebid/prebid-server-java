package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonParseException;
import io.netty.util.AsciiString;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.UidWithExpiry;
import org.prebid.server.model.Uids;
import org.prebid.server.model.request.CookieSyncRequest;
import org.prebid.server.model.response.BidderStatus;
import org.prebid.server.model.response.CookieSyncResponse;
import org.prebid.server.model.response.UsersyncInfo;
import org.prebid.server.usersyncer.AppnexusUsersyncer;
import org.prebid.server.usersyncer.RubiconUsersyncer;
import org.prebid.server.usersyncer.UsersyncerCatalog;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
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
    private UsersyncerCatalog usersyncerCatalog;
    @Mock
    private RubiconUsersyncer rubiconUsersyncer;
    @Mock
    private AppnexusUsersyncer appnexusUsersyncer;
    @Mock
    private Metrics metrics;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;

    private CookieSyncHandler cookieSyncHandler;

    @Before
    public void setUp() {
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build()));

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);

        cookieSyncHandler = new CookieSyncHandler(uidsCookieService, usersyncerCatalog, metrics);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new CookieSyncHandler(null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new CookieSyncHandler(uidsCookieService, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new CookieSyncHandler(uidsCookieService, usersyncerCatalog, null));
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
        verifyNoMoreInteractions(httpResponse, usersyncerCatalog);
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBodyAsJson())
                .willThrow(new DecodeException("Could not parse", new JsonParseException(null, (String) null)));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).setStatusMessage(eq("JSON parse failed"));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse, usersyncerCatalog);
    }

    @Test
    public void shouldRespondWithErrorIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBodyAsJson()).willReturn(null);

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse, usersyncerCatalog);
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        given(routingContext.getBodyAsJson())
                .willReturn(JsonObject.mapFrom(CookieSyncRequest.of(null, emptyList())));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse)
                .putHeader(eq(new AsciiString("Content-Type")), eq(new AsciiString("application/json")));
    }

    @Test
    public void shouldRespondWithSomeBidderStatusesIfSomeUidsMissingInCookies() throws IOException {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder().uids(uids).build()));

        given(routingContext.getBodyAsJson()).willReturn(JsonObject.mapFrom(
                CookieSyncRequest.of("uuid", asList(RUBICON, APPNEXUS))));

        givenUsersyncersReturningFamilyName();

        final UsersyncInfo appnexusUsersyncInfo = UsersyncInfo.of("http://adnxsexample.com", "redirect", false);
        given(usersyncerCatalog.byName(APPNEXUS).usersyncInfo()).willReturn(appnexusUsersyncInfo);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("uuid", "ok",
                singletonList(BidderStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(appnexusUsersyncInfo)
                        .build())));
    }

    @Test
    public void shouldRespondWithNoBidderStatusesIfAllUidsPresentInCookies() throws IOException {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        uids.put(APPNEXUS_COOKIE, UidWithExpiry.live("12345"));
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder().uids(uids).build()));

        given(routingContext.getBodyAsJson()).willReturn(JsonObject.mapFrom(
                CookieSyncRequest.of("uuid", asList(RUBICON, APPNEXUS))));

        givenUsersyncersReturningFamilyName();

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("uuid", "ok", emptyList()));
    }

    @Test
    public void shouldTolerateUnsupportedBidder() throws IOException {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        uids.put(APPNEXUS_COOKIE, UidWithExpiry.live("12345"));
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder().uids(uids).build()));

        given(routingContext.getBodyAsJson()).willReturn(JsonObject.mapFrom(
                CookieSyncRequest.of("uuid", asList(RUBICON, "unsupported"))));

        givenUsersyncersReturningFamilyName();

        given(usersyncerCatalog.isValidName("unsupported")).willReturn(false);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("uuid", "ok", emptyList()));
    }

    @Test
    public void shouldRespondWithNoCookieStatusIfNoLiveUids() throws IOException {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(APPNEXUS_COOKIE, UidWithExpiry.expired("12345"));
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(Uids.builder().uids(uids).build()));

        given(routingContext.getBodyAsJson()).willReturn(JsonObject.mapFrom(
                CookieSyncRequest.of("uuid", singletonList(APPNEXUS))));

        givenUsersyncersReturningFamilyName();

        final UsersyncInfo appnexusUsersyncInfo = UsersyncInfo.of("http://adnxsexample.com", "redirect", false);
        given(usersyncerCatalog.byName(APPNEXUS).usersyncInfo()).willReturn(appnexusUsersyncInfo);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("uuid", "no_cookie",
                singletonList(BidderStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(appnexusUsersyncInfo)
                        .build())));
    }

    @Test
    public void shouldIncrementMetrics() {
        // given
        given(routingContext.getBodyAsJson())
                .willReturn(JsonObject.mapFrom(CookieSyncRequest.of(null, emptyList())));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).incCounter(eq(MetricName.cookie_sync_requests));
    }

    private void givenUsersyncersReturningFamilyName() {
        given(usersyncerCatalog.byName(eq(RUBICON))).willReturn(rubiconUsersyncer);
        given(usersyncerCatalog.isValidName(eq(RUBICON))).willReturn(true);
        given(usersyncerCatalog.byName(eq(APPNEXUS))).willReturn(appnexusUsersyncer);
        given(usersyncerCatalog.isValidName(eq(APPNEXUS))).willReturn(true);

        given(rubiconUsersyncer.cookieFamilyName()).willReturn(RUBICON);
        given(rubiconUsersyncer.name()).willReturn(RUBICON);

        given(appnexusUsersyncer.cookieFamilyName()).willReturn(APPNEXUS_COOKIE);
        given(appnexusUsersyncer.name()).willReturn(APPNEXUS);
    }

    private CookieSyncResponse captureCookieSyncResponse() throws IOException {
        final ArgumentCaptor<String> cookieSyncResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).end(cookieSyncResponseCaptor.capture());
        return mapper.readValue(cookieSyncResponseCaptor.getValue(), CookieSyncResponse.class);
    }
}
