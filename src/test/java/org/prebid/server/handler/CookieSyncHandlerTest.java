package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.util.AsciiString;
import io.vertx.core.Future;
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
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.model.GdprResponse;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CookieSyncHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";

    private static final String APPNEXUS = "appnexus";
    private static final String APPNEXUS_COOKIE = "adnxs";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final TimeoutFactory timeoutFactory =
            new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()));

    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private GdprService gdprService;
    @Mock
    private AnalyticsReporter analyticsReporter;
    @Mock
    private Metrics metrics;

    private CookieSyncHandler cookieSyncHandler;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;

    private Usersyncer rubiconUsersyncer;

    private Usersyncer appnexusUsersyncer;

    @Before
    public void setUp() {
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        cookieSyncHandler = new CookieSyncHandler(
                "http://external-url",
                2000,
                1, false, false, emptyList(), uidsCookieService,
                bidderCatalog,
                gdprService,
                analyticsReporter,
                metrics,
                timeoutFactory,
                jacksonMapper);
    }

    @Test
    public void shouldRespondWithErrorIfOptedOut() {
        // given
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build(), jacksonMapper));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).setStatusMessage(eq("User has opted out"));
        verify(httpResponse).end();
        verify(routingContext, never()).getBody();
        verifyNoMoreInteractions(httpResponse, gdprService);
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
        verifyNoMoreInteractions(httpResponse, gdprService);
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
        verifyNoMoreInteractions(httpResponse, gdprService);
    }

    @Test
    public void shouldRespondWithErrorIfGdprConsentIsMissing() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(emptyList(), 1, null, null, null)));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).setStatusMessage(eq("gdpr_consent is required if gdpr is 1"));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse, gdprService);
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.of(emptyList(), null, null, null, null)));

        givenGdprServiceReturningResult(emptyMap());

        given(routingContext.response().closed()).willReturn(true);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).end();
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.of(emptyList(), null, null, null, null)));

        givenGdprServiceReturningResult(emptyMap());

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
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS), null, null, null, null)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", null, null, "redirect", false);
        rubiconUsersyncer = new Usersyncer(RUBICON, "", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("ok",
                singletonList(BidderUsersyncStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of("http://adnxsexample.com", "redirect", false))
                        .build())));
    }

    @Test
    public void shouldRespondWithAllActiveBiddersWhenRequestCoopSyncTrueAndNoPriorityConfigured() throws IOException {
        // given
        final String disabledBidder = "disabled_bidder";
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList(APPNEXUS, RUBICON, disabledBidder)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.isActive(disabledBidder)).willReturn(false);

        cookieSyncHandler = new CookieSyncHandler(
                "http://external-url",
                2000,
                1,
                false,
                false,
                emptyList(),
                uidsCookieService,
                bidderCatalog,
                gdprService,
                analyticsReporter,
                metrics,
                timeoutFactory,
                jacksonMapper);

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(singletonList(APPNEXUS), null, null, true, null)));

        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", null, null, "redirect", false);
        rubiconUsersyncer = new Usersyncer(RUBICON, "http://rubiconexample.com", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(
                        UsersyncInfo.of("http://adnxsexample.com", "redirect", false)).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(
                        UsersyncInfo.of("http://rubiconexample.com", "redirect", false)).build())));
    }

    @Test
    public void shouldRespondWithCoopBiddersWhenRequestCoopSyncTrue() throws IOException {
        // given
        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final String disabledBidder = "disabled_bidder";
        given(bidderCatalog.isActive(disabledBidder)).willReturn(false);

        final List<Collection<String>> coopBidders = asList(singletonList(RUBICON), singletonList(disabledBidder));

        cookieSyncHandler = new CookieSyncHandler(
                "http://external-url",
                2000,
                1,
                false,
                false,
                coopBidders,
                uidsCookieService,
                bidderCatalog,
                gdprService,
                analyticsReporter,
                metrics,
                timeoutFactory,
                jacksonMapper);

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(singletonList(APPNEXUS), null, null, true, null)));

        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", null, null, "redirect", false);
        rubiconUsersyncer = new Usersyncer(RUBICON, "http://rubiconexample.com", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(disabledBidder).error("Unsupported bidder").build(),
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(
                        UsersyncInfo.of("http://adnxsexample.com", "redirect", false)).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(
                        UsersyncInfo.of("http://rubiconexample.com", "redirect", false)).build())));
    }

    @Test
    public void shouldRespondWithPrioritisedCoopBidderWhenRequestCoopDefaultTrueAndLimitIsLessThanCoopSize() throws IOException {
        // given
        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final List<Collection<String>> priorityBidders = asList(singletonList(APPNEXUS), singletonList(RUBICON),
                asList("bidder1", "bidder2"), singletonList("spam"));

        cookieSyncHandler = new CookieSyncHandler(
                "http://external-url",
                2000,
                1,
                false,
                true,
                priorityBidders,
                uidsCookieService,
                bidderCatalog,
                gdprService,
                analyticsReporter,
                metrics,
                timeoutFactory,
                jacksonMapper);

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(singletonList(APPNEXUS), null, null, null, 2)));

        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", null, null, "redirect", false);
        rubiconUsersyncer = new Usersyncer(RUBICON, "http://rubiconexample.com", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(
                        UsersyncInfo.of("http://adnxsexample.com", "redirect", false)).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(
                        UsersyncInfo.of("http://rubiconexample.com", "redirect", false)).build())));
    }

    @Test
    public void shouldRespondWithBidderStatusForAllBiddersIfBiddersListOmittedInRequest() throws IOException {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(null, null, null, null, null)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final String disabledBidder = "disabled_bidder";
        given(bidderCatalog.isActive(disabledBidder)).willReturn(false);

        given(bidderCatalog.names()).willReturn(new HashSet<>(asList(APPNEXUS, RUBICON, disabledBidder)));

        cookieSyncHandler = new CookieSyncHandler(
                "http://external-url",
                2000,
                1,
                false,
                false,
                emptyList(),
                uidsCookieService,
                bidderCatalog,
                gdprService,
                analyticsReporter,
                metrics,
                timeoutFactory,
                jacksonMapper);

        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", null, null, "redirect", false);
        rubiconUsersyncer = new Usersyncer(RUBICON, "http://rubiconexample.com", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(
                        UsersyncInfo.of("http://adnxsexample.com", "redirect", false)).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(
                        UsersyncInfo.of("http://rubiconexample.com", "redirect", false)).build())));
    }

    @Test
    public void shouldRespondWithNoBidderStatusesIfAllUidsPresentInCookies() throws IOException {
        // given
        final Map<String, UidWithExpiry> uids = new HashMap<>();
        uids.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        uids.put(APPNEXUS_COOKIE, UidWithExpiry.live("12345"));
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(uids).build(), jacksonMapper));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS), null, null, null, null)));

        rubiconUsersyncer = new Usersyncer(RUBICON, "", null, null, null, false);
        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "", null, null, null, false);
        givenUsersyncersReturningFamilyName();


        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("ok", emptyList()));
    }

    @Test
    public void shouldTolerateUnsupportedBidder() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(
                        CookieSyncRequest.of(asList(RUBICON, "unsupported"), null, null, null, null)));

        rubiconUsersyncer = new Usersyncer(RUBICON, "", null, null, null, false);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isValidName("unsupported")).willReturn(false);

        givenGdprServiceReturningResult(singletonMap(RUBICON, 1));

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
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(
                        CookieSyncRequest.of(asList(RUBICON, "disabled"), null, null, null, null)));

        rubiconUsersyncer = new Usersyncer(RUBICON, "", null, null, null, false);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isValidName(RUBICON)).willReturn(true);
        given(bidderCatalog.isValidName("disabled")).willReturn(true);

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive("disabled")).willReturn(false);

        givenGdprServiceReturningResult(singletonMap(RUBICON, 1));

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
    public void shouldTolerateRejectedBidderByGdpr() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS), null, null, null, null)));

        rubiconUsersyncer = new Usersyncer(RUBICON, "", null, null, null, false);
        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "", null, null, null, false);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        given(bidderCatalog.bidderInfoByName(APPNEXUS))
                .willReturn(BidderInfo.create(true, null, null,
                        null, null, 2, true, false));

        givenGdprServiceReturningResult(singletonMap(RUBICON, 1));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("ok");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsOnly(tuple(APPNEXUS, "Rejected by GDPR"));
    }

    @Test
    public void shouldUpdateCookieSyncSetAndRejectByGdprMetricForEachRejectedAndSyncedBidder() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS), null, null, null, null)));

        rubiconUsersyncer = new Usersyncer(RUBICON, "", null, null, null, false);
        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "", null, null, null, false);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        given(bidderCatalog.bidderInfoByName(APPNEXUS))
                .willReturn(BidderInfo.create(true, null, null,
                        null, null, 2, true, false));

        givenGdprServiceReturningResult(singletonMap(RUBICON, 1));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).updateCookieSyncGdprPreventMetric(APPNEXUS);
        verify(metrics).updateCookieSyncGenMetric(RUBICON);
    }

    @Test
    public void shouldUpdateCookieSyncMatchesMetricForEachAlreadySyncedBidder() {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS), null, null, null, null)));

        rubiconUsersyncer = new Usersyncer(RUBICON, "", null, null, null, false);
        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "", null, null, null, false);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        Map<String, Integer> bidderToGdprVendorId = new HashMap<>();
        bidderToGdprVendorId.put(RUBICON, 1);
        bidderToGdprVendorId.put(APPNEXUS, 2);
        givenGdprServiceReturningResult(bidderToGdprVendorId);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).updateCookieSyncMatchesMetric(RUBICON);
        verify(metrics, never()).updateCookieSyncMatchesMetric(APPNEXUS);
    }

    @Test
    public void shouldRespondWithNoCookieStatusIfHostVendorRejectedByGdpr() throws IOException {
        // given
        cookieSyncHandler = new CookieSyncHandler(
                "http://external-url",
                2000,
                null,
                false,
                false,
                emptyList(),
                uidsCookieService,
                bidderCatalog,
                gdprService,
                analyticsReporter,
                metrics,
                timeoutFactory,
                jacksonMapper);

        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS), null, null, null, null)));

        rubiconUsersyncer = new Usersyncer(RUBICON, "", null, null, null, false);
        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "", null, null, null, false);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("no_cookie");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(2)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsOnly(
                        tuple(RUBICON, "Rejected by GDPR"),
                        tuple(APPNEXUS, "Rejected by GDPR"));
    }

    @Test
    public void shouldRespondWithNoCookieStatusIfNoLiveUids() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(APPNEXUS_COOKIE, UidWithExpiry.expired("12345"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(singletonList(APPNEXUS), null, null, null, null)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie",
                singletonList(BidderUsersyncStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of("http://adnxsexample.com", "redirect", false))
                        .build())));
    }

    @Test
    public void shouldRespondWithExpectedUsersyncInfo() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(singletonList(APPNEXUS), 1, "gdpr_consent1", null, null)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        appnexusUsersyncer = new Usersyncer(
                APPNEXUS_COOKIE, "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}", null, null,
                "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(bidderStatus -> bidderStatus.getUsersync().getUrl())
                .containsOnly("http://adnxsexample.com/sync?gdpr=1&gdpr_consent=gdpr_consent1");
    }

    @Test
    public void shouldRespondWithUpdatedUsersyncInfoIfHostCookieAndUidsDiffers() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(singletonList(RUBICON), null, null, null, null)));

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        rubiconUsersyncer = new Usersyncer(RUBICON, "http://rubiconexample.com", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(singletonMap(RUBICON, 1));

        given(uidsCookieService.getHostCookieFamily()).willReturn(RUBICON);
        given(uidsCookieService.parseHostCookie(any())).willReturn("host/cookie/value");
        given(uidsCookieService.parseUids(any()))
                .willReturn(Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("uid-cookie-value"))).build());

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(BidderUsersyncStatus::getUsersync)
                .containsOnly(
                        UsersyncInfo.of("http://external-url/setuid?bidder=rubicon&gdpr=&gdpr_consent="
                                + "&uid=host%2Fcookie%2Fvalue", "redirect", false));
    }

    @Test
    public void shouldRespondWithOriginalUsersyncInfoIfNoHostCookieFamilyInBiddersCookieFamily() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(singletonList(APPNEXUS), 1, "gdpr_consent1", null, null)));

        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);
        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}", null, null, "redirect",
                false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        given(uidsCookieService.getHostCookieFamily()).willReturn(RUBICON);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(BidderUsersyncStatus::getUsersync)
                .containsOnly(UsersyncInfo.of("http://adnxsexample.com/sync?gdpr=1&gdpr_consent=gdpr_consent1",
                        "redirect", false));
    }

    @Test
    public void shouldRespondWithOriginalUsersyncInfoIfNoHostCookieInRequest() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(singletonList(RUBICON), null, null, null, null)));

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);

        rubiconUsersyncer = new Usersyncer(RUBICON, "http://rubiconexample.com", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(singletonMap(RUBICON, 1));

        given(uidsCookieService.getHostCookieFamily()).willReturn(RUBICON);
        given(uidsCookieService.parseHostCookie(any())).willReturn(null);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(BidderUsersyncStatus::getUsersync)
                .containsOnly(
                        UsersyncInfo.of("http://rubiconexample.com", "redirect", false));
    }

    @Test
    public void shouldRespondWithOriginalUsersyncInfoIfHostCookieAndUidsAreEqual() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(singletonList(RUBICON), null, null, null, null)));

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);

        rubiconUsersyncer = new Usersyncer(RUBICON, "http://rubiconexample.com", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(singletonMap(RUBICON, 1));

        given(uidsCookieService.getHostCookieFamily()).willReturn(RUBICON);
        given(uidsCookieService.parseHostCookie(any())).willReturn("cookie-value");
        given(uidsCookieService.parseUids(any()))
                .willReturn(Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("cookie-value"))).build());

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(BidderUsersyncStatus::getUsersync)
                .containsOnly(UsersyncInfo.of("http://rubiconexample.com", "redirect", false));
    }

    @Test
    public void shouldRespondWithExpectedUsersyncInfoForBidderAlias() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(singletonList("rubiconAlias"), 0, null, null, null)));

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isAlias("rubiconAlias")).willReturn(true);
        given(bidderCatalog.nameByAlias("rubiconAlias")).willReturn(RUBICON);

        rubiconUsersyncer = new Usersyncer(RUBICON, "http://rubiconexample.com", null, null, "redirect", false);
        givenUsersyncersReturningFamilyName();
        givenGdprServiceReturningResult(singletonMap(RUBICON, 1));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(bidderStatus -> bidderStatus.getUsersync().getUrl())
                .containsOnly("http://rubiconexample.com");
    }

    @Test
    public void shouldTolerateMissingGdprParamsInRequestForUsersyncInfo() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(singletonList(APPNEXUS), null, "", null, null)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.names()).willReturn(singleton(APPNEXUS));

        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}", null, null, "redirect",
                false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(bidderStatus -> bidderStatus.getUsersync().getUrl())
                .containsOnly("http://adnxsexample.com/sync?gdpr=&gdpr_consent=");
    }

    @Test
    public void shouldLimitBidderStatuses() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(asList(RUBICON, APPNEXUS), 0, null, null, 1)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        rubiconUsersyncer = new Usersyncer(RUBICON,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                null, null, "redirect", false);
        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://rubiconexample.com", null, null, "redirect",
                false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1);
    }

    @Test
    public void shouldLimitBidderStatusesWithLiveUids() throws IOException {
        // given
        Map<String, UidWithExpiry> liveUids = doubleMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"),
                APPNEXUS_COOKIE, UidWithExpiry.live("1234567890"));
        given(uidsCookieService.parseFromRequest(any())).willReturn(new UidsCookie(
                Uids.builder().uids(liveUids).build(), jacksonMapper));

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(asList(RUBICON, APPNEXUS), 0, null, null, 1)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        rubiconUsersyncer = new Usersyncer(RUBICON,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                null, null, "redirect", false);
        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://rubiconexample.com", null, null, "redirect",
                false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).isEmpty();
    }

    @Test
    public void shouldNotLimitBidderStatusesIfLimitIsBiggerThanBiddersList() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.of(asList(RUBICON, APPNEXUS), 0, null, null, 3)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        rubiconUsersyncer = new Usersyncer(RUBICON,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                null, null, "redirect", false);
        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://rubiconexample.com", null, null, "redirect",
                false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(2);
    }

    @Test
    public void shouldIncrementMetrics() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.of(emptyList(), null, null, null, null)));

        givenGdprServiceReturningResult(emptyMap());

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).updateCookieSyncRequestMetric();
    }

    @Test
    public void shouldPassUnauthorizedEventToAnalyticsReporterIfOptedOut() {
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build(), jacksonMapper));

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
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.of(asList(RUBICON, APPNEXUS), null, null, null, null)));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        appnexusUsersyncer = new Usersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", null, null, "redirect", false);
        rubiconUsersyncer = new Usersyncer(RUBICON, "", null, null, null, false);
        givenUsersyncersReturningFamilyName();

        givenGdprServiceReturningResult(doubleMap(RUBICON, 1, APPNEXUS, 2));

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

    private void givenGdprServiceReturningResult(Map<String, Integer> bidderToGdprVendorId) {
        final Map<Integer, Boolean> vendorToGdprResult = new HashMap<>();

        for (Map.Entry<String, Integer> entry : bidderToGdprVendorId.entrySet()) {
            given(bidderCatalog.bidderInfoByName(entry.getKey()))
                    .willReturn(BidderInfo.create(true, null, null,
                            null, null, entry.getValue(), true, false));

            vendorToGdprResult.put(entry.getValue(), true);
        }

        given(gdprService.resultByVendor(anySet(), anySet(), any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(GdprResponse.of(true, vendorToGdprResult, null)));
    }

    private static Buffer givenRequestBody(CookieSyncRequest request) {
        try {
            return Buffer.buffer(mapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }
    }

    private void givenUsersyncersReturningFamilyName() {
        given(bidderCatalog.isValidName(RUBICON)).willReturn(true);
        given(bidderCatalog.usersyncerByName(RUBICON)).willReturn(rubiconUsersyncer);

        given(bidderCatalog.isValidName(APPNEXUS)).willReturn(true);
        given(bidderCatalog.usersyncerByName(APPNEXUS)).willReturn(appnexusUsersyncer);
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

    @SuppressWarnings("SameParameterValue")
    private static <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
}