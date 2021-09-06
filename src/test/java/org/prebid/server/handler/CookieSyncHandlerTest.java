package org.prebid.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.TextNode;
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
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.EnabledForRequestType;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.apache.commons.lang3.StringUtils.EMPTY;
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
import static org.mockito.Mockito.verifyZeroInteractions;

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
    private ApplicationSettings applicationSettings;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private TcfDefinerService tcfDefinerService;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private AnalyticsReporterDelegator analyticsReporterDelegator;
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
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(AsciiString.class))).willReturn(httpResponse);

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(
                        Privacy.of("", EMPTY, Ccpa.EMPTY, 0),
                        TcfContext.empty())));

        cookieSyncHandler = new CookieSyncHandler(
                "http://external-url",
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                tcfDefinerService,
                privacyEnforcementService,
                1,
                false,
                emptyList(),
                analyticsReporterDelegator,
                metrics,
                timeoutFactory,
                jacksonMapper);
    }

    @Test
    public void shouldRespondWithErrorAndSendToAnalyticsWithoutTcfWhenRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).closed();
        verify(httpResponse).setStatusCode(400);
        verify(httpResponse).end("Invalid request format: Request has no body");
        verify(metrics).updateUserSyncBadRequestMetric();
        verifyNoMoreInteractions(httpResponse, tcfDefinerService);

        final CookieSyncEvent cookieSyncEvent = captureCookieSyncEvent();
        assertThat(cookieSyncEvent)
                .isEqualTo(CookieSyncEvent.error(400, "Invalid request format: Request has no body"));
    }

    @Test
    public void shouldRespondWithErrorAndSendToAnalyticsWithoutTcfWhenRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{"));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).closed();
        verify(httpResponse).setStatusCode(400);
        verify(httpResponse).end("Invalid request format: Request body cannot be parsed");
        verify(metrics).updateUserSyncBadRequestMetric();
        verifyNoMoreInteractions(httpResponse, tcfDefinerService);

        final CookieSyncEvent cookieSyncEvent = captureCookieSyncEvent();
        assertThat(cookieSyncEvent)
                .isEqualTo(CookieSyncEvent.error(400, "Invalid request format: Request body cannot be parsed"));
    }

    @Test
    public void shouldRespondWithErrorAndSendToAnalyticsWithTcfWhenOptedOut() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(emptyList()).gdpr(1).build()));
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build(), jacksonMapper));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).closed();
        verify(httpResponse).setStatusCode(401);
        verify(httpResponse).end("Unauthorized: Sync is not allowed for this uids");
        verifyNoMoreInteractions(httpResponse, tcfDefinerService);

        final CookieSyncEvent cookieSyncEvent = captureCookieSyncTcfEvent();
        assertThat(cookieSyncEvent)
                .isEqualTo(CookieSyncEvent.error(401, "Unauthorized: Sync is not allowed for this uids"));
    }

    @Test
    public void shouldRespondWithErrorIfGdprConsentIsMissing() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(emptyList()).gdpr(1).build()));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse).closed();
        verify(httpResponse).setStatusCode(400);
        verify(httpResponse).end("Invalid request format: gdpr_consent is required if gdpr is 1");
        verify(metrics).updateUserSyncBadRequestMetric();
        verifyNoMoreInteractions(httpResponse, tcfDefinerService);

        final CookieSyncEvent cookieSyncEvent = captureCookieSyncTcfEvent();
        assertThat(cookieSyncEvent)
                .isEqualTo(CookieSyncEvent.error(400, "Invalid request format: gdpr_consent is required if gdpr is 1"));
    }

    @Test
    public void shouldRespondWithBadRequestStatusIfGdprConsentIsInvalid() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder()
                        .bidders(singletonList(RUBICON))
                        .gdpr(1)
                        .gdprConsent("invalid")
                        .build()));

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(null,
                        TcfContext.builder().gdpr("1").isConsentValid(false).build())));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).updateUserSyncTcfInvalidMetric();
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Consent string is invalid"));
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.builder().bidders(emptyList()).build()));

        givenTcfServiceReturningVendorIdResult(emptySet());

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
                givenRequestBody(CookieSyncRequest.builder().bidders(emptyList()).build()));

        givenTcfServiceReturningVendorIdResult(emptySet());

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(httpResponse)
                .putHeader(new AsciiString("Content-Type"), new AsciiString("application/json"));
    }

    @Test
    public void shouldPassAccountToPrivacyEnforcementServiceWhenAccountIsFound() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.builder().bidders(emptyList()).account("account").build()));

        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabledForRequestType(EnabledForRequestType.of(true, true, true, true)).build();
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(null, accountGdprConfig, null))
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        givenTcfServiceReturningVendorIdResult(singleton(1));
        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.failedFuture("fail"));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(applicationSettings).getAccountById(eq("account"), any());

        verify(privacyEnforcementService).contextFromCookieSyncRequest(any(), any(), eq(account), any());
    }

    @Test
    public void shouldPassAccountToPrivacyEnforcementServiceWhenAccountIsNotFound() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.builder().bidders(emptyList()).account("account").build()));

        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.failedFuture("bad"));

        givenTcfServiceReturningVendorIdResult(singleton(1));
        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.failedFuture("fail"));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(applicationSettings).getAccountById(eq("account"), any());

        verify(privacyEnforcementService)
                .contextFromCookieSyncRequest(any(), any(), eq(Account.empty("account")), any());
    }

    @Test
    public void shouldRespondWithSomeBidderStatusesIfSomeUidsMissingInCookies() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", "redirect");
        rubiconUsersyncer = createUsersyncer(RUBICON, "url", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

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

        cookieSyncHandler = new CookieSyncHandler("http://external-url", 2000, uidsCookieService, applicationSettings,
                bidderCatalog, tcfDefinerService, privacyEnforcementService, 1, false, emptyList(),
                analyticsReporterDelegator, metrics, timeoutFactory, jacksonMapper);

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(APPNEXUS)).coopSync(true).build()));

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", "redirect");
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

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

        cookieSyncHandler = new CookieSyncHandler("http://external-url", 2000, uidsCookieService, applicationSettings,
                bidderCatalog, tcfDefinerService, privacyEnforcementService, 1, false, coopBidders,
                analyticsReporterDelegator, metrics, timeoutFactory, jacksonMapper);

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(APPNEXUS)).coopSync(true).build()));

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", "redirect");
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

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
    public void shouldRespondWithCoopBiddersWhenAccountCoopSyncTrue() throws IOException {
        // given
        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final List<Collection<String>> coopBidders = singletonList(singletonList(RUBICON));

        cookieSyncHandler = new CookieSyncHandler("http://external-url", 2000, uidsCookieService, applicationSettings,
                bidderCatalog, tcfDefinerService, privacyEnforcementService, 1, false, coopBidders,
                analyticsReporterDelegator, metrics, timeoutFactory, jacksonMapper);

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder()
                        .bidders(singletonList(APPNEXUS))
                        .account("account")
                        .build()));

        given(applicationSettings.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(
                Account.builder()
                        .cookieSync(AccountCookieSyncConfig.of(null, null, true))
                        .build()));

        appnexusUsersyncer = Usersyncer.of(APPNEXUS_COOKIE,
                Usersyncer.UsersyncMethod.of("redirect", "http://adnxsexample.com", null, false), null);
        rubiconUsersyncer = Usersyncer.of(RUBICON,
                Usersyncer.UsersyncMethod.of("redirect", "http://rubiconexample.com", null, false), null);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

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
    public void shouldRespondWithPrioritisedCoopBidderWhenRequestCoopDefaultTrueAndLimitIsLessThanCoopSize()
            throws IOException {
        // given
        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final List<Collection<String>> priorityBidders = asList(singletonList(APPNEXUS), singletonList(RUBICON),
                asList("bidder1", "bidder2"), singletonList("spam"));

        cookieSyncHandler = new CookieSyncHandler("http://external-url", 2000, uidsCookieService, applicationSettings,
                bidderCatalog, tcfDefinerService, privacyEnforcementService, 1, true, priorityBidders,
                analyticsReporterDelegator, metrics, timeoutFactory, jacksonMapper);

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(APPNEXUS)).limit(2).build()));

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", "redirect");
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

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
                .willReturn(givenRequestBody(CookieSyncRequest.builder().build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final String disabledBidder = "disabled_bidder";
        given(bidderCatalog.isActive(disabledBidder)).willReturn(false);

        given(bidderCatalog.names()).willReturn(new HashSet<>(asList(APPNEXUS, RUBICON, disabledBidder)));

        cookieSyncHandler = new CookieSyncHandler("http://external-url", 2000, uidsCookieService, applicationSettings,
                bidderCatalog, tcfDefinerService, privacyEnforcementService, 1, false, emptyList(),
                analyticsReporterDelegator, metrics, timeoutFactory, jacksonMapper);

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", "redirect");
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

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
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(uids).build(), jacksonMapper));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "", null);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("ok", emptyList()));
    }

    @Test
    public void shouldTolerateUnsupportedBidder() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(asList(RUBICON, "unsupported")).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isValidName("unsupported")).willReturn(false);

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(RUBICON));

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
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, "disabled")).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isValidName(RUBICON)).willReturn(true);
        given(bidderCatalog.isValidName("disabled")).willReturn(true);

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive("disabled")).willReturn(false);

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(RUBICON));

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
    public void shouldTolerateRejectedBidderByTcf() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        given(bidderCatalog.bidderInfoByName(APPNEXUS))
                .willReturn(BidderInfo.create(true, null, null, null, null, null, null, 2, true, true, false));

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("ok");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsOnly(tuple(APPNEXUS, "Rejected by TCF"));
    }

    @Test
    public void shouldTolerateBiddersWithoutUsersyncUrl() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap("notRelevantBidder", UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "", null);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("ok", emptyList()));
    }

    @Test
    public void shouldSkipVendorHostCheckAndContinueWithBiddersCheckWhenHostVendorIdIsMissing() throws IOException {
        // given
        cookieSyncHandler = new CookieSyncHandler("http://external-url", 2000, uidsCookieService, applicationSettings,
                bidderCatalog, tcfDefinerService, privacyEnforcementService, null, false, emptyList(),
                analyticsReporterDelegator, metrics, timeoutFactory, jacksonMapper);

        final Uids uids = Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(uids, jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        given(bidderCatalog.bidderInfoByName(APPNEXUS))
                .willReturn(BidderInfo.create(true, null, null, null,
                        null, null, null, 2, true, true, false));

        givenTcfServiceReturningBidderNamesResult(singleton(RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(tcfDefinerService, never()).resultForVendorIds(anySet(), any());
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("ok");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsOnly(tuple(APPNEXUS, "Rejected by TCF"));
    }

    @Test
    public void shouldUpdateCookieSyncSetAndRejectByTcfMetricForEachRejectedAndSyncedBidder() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        given(bidderCatalog.bidderInfoByName(APPNEXUS))
                .willReturn(BidderInfo.create(true, null, null, null, null, null, null, 2, true, true, false));

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).updateCookieSyncTcfBlockedMetric(APPNEXUS);
        verify(metrics).updateCookieSyncGenMetric(RUBICON);
    }

    @Test
    public void shouldUpdateCookieSyncMatchesMetricForEachAlreadySyncedBidder() {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "url", null);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "url", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).updateCookieSyncMatchesMetric(RUBICON);
        verify(metrics, never()).updateCookieSyncMatchesMetric(APPNEXUS);
    }

    @Test
    public void shouldRespondWithNoCookieStatusIfHostVendorRejectedByTcf() throws IOException {
        // given
        cookieSyncHandler = new CookieSyncHandler("http://external-url", 2000, uidsCookieService, applicationSettings,
                bidderCatalog, tcfDefinerService, privacyEnforcementService, 1, false, emptyList(),
                analyticsReporterDelegator, metrics, timeoutFactory, jacksonMapper);

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        givenTcfServiceReturningBlockedVendorIdResult(set(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("no_cookie");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(2)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsOnly(
                        tuple(RUBICON, "Rejected by TCF"),
                        tuple(APPNEXUS, "Rejected by TCF"));
    }

    @Test
    public void shouldRespondWithNoCookieStatusIfNoLiveUids() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(APPNEXUS_COOKIE, UidWithExpiry.expired("12345"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(singletonList(APPNEXUS)).build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(APPNEXUS));

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
                CookieSyncRequest.builder()
                        .bidders(singletonList(APPNEXUS))
                        .gdpr(1)
                        .gdprConsent("gdpr_consent1")
                        .build()));

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(
                        Privacy.of("1", "gdpr_consent1", Ccpa.EMPTY, 0),
                        TcfContext.empty())));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        appnexusUsersyncer = createUsersyncer(
                APPNEXUS_COOKIE,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(bidderStatus -> bidderStatus.getUsersync().getUrl())
                .containsOnly("http://adnxsexample.com/sync?gdpr=1&gdpr_consent=gdpr_consent1");
    }

    @Test
    public void shouldRespondWithUsersyncMethodAllowedByRequest() throws IOException {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder()
                        .bidders(singletonList(RUBICON))
                        .filterSettings(CookieSyncRequest.FilterSettings.of(
                                CookieSyncRequest.MethodFilter.of(
                                        new TextNode("*"),
                                        CookieSyncRequest.FilterType.exclude),
                                null))
                        .build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        rubiconUsersyncer = Usersyncer.of(
                RUBICON,
                Usersyncer.UsersyncMethod.of("iframe", "iframe-url", null, false),
                Usersyncer.UsersyncMethod.of("redirect", "redirect-url", null, false));
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie",
                singletonList(BidderUsersyncStatus.builder()
                        .bidder(RUBICON)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of("redirect-url", "redirect", false))
                        .build())));
    }

    @Test
    public void shouldRespondWithUpdatedUsersyncInfoIfHostCookieAndUidsDiffers() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(RUBICON)).build()));

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(
                        Privacy.of("", EMPTY, Ccpa.EMPTY, 0),
                        TcfContext.empty())));

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(RUBICON));

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
                        UsersyncInfo.of("http://external-url/setuid?bidder=rubicon&gdpr=&gdpr_consent=&us_privacy="
                                + "&f=i&uid=host%2Fcookie%2Fvalue", "redirect", false));
    }

    @Test
    public void shouldRespondWithOriginalUsersyncInfoIfNoHostCookieFamilyInBiddersCookieFamily() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder()
                        .bidders(singletonList(APPNEXUS))
                        .gdpr(1)
                        .gdprConsent("gdpr_consent1")
                        .usPrivacy("YNN")
                        .build()));

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(
                        Privacy.of("1", "gdpr_consent1", Ccpa.of("YNN"), 0),
                        TcfContext.empty())));

        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);
        appnexusUsersyncer = createUsersyncer(
                APPNEXUS_COOKIE,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}&us_privacy={{us_privacy}}",
                "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(APPNEXUS));

        given(uidsCookieService.getHostCookieFamily()).willReturn(RUBICON);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(BidderUsersyncStatus::getUsersync)
                .containsOnly(
                        UsersyncInfo.of("http://adnxsexample.com/sync?gdpr=1&gdpr_consent=gdpr_consent1&us_privacy=YNN",
                                "redirect", false));
    }

    @Test
    public void shouldRespondWithOriginalUsersyncInfoIfNoHostCookieInRequest() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(RUBICON)).build()));

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);

        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(RUBICON));

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
                CookieSyncRequest.builder().bidders(singletonList(RUBICON)).build()));

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);

        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

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
    public void shouldTolerateMissingTcfParamsInRequestForUsersyncInfo() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(APPNEXUS)).gdprConsent(EMPTY).build()));

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(
                        Privacy.of("", EMPTY, Ccpa.EMPTY, 0),
                        TcfContext.empty())));

        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.names()).willReturn(singleton(APPNEXUS));

        appnexusUsersyncer = createUsersyncer(
                APPNEXUS_COOKIE,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(bidderStatus -> bidderStatus.getUsersync().getUrl())
                .containsOnly("http://adnxsexample.com/sync?gdpr=&gdpr_consent=");

        verifyZeroInteractions(applicationSettings);
    }

    @Test
    public void shouldLimitBidderStatuses() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).gdpr(0).limit(1).build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(
                Account.builder()
                        .cookieSync(AccountCookieSyncConfig.of(5, 5, null))
                        .build()));

        rubiconUsersyncer = Usersyncer.of(RUBICON,
                Usersyncer.UsersyncMethod.of("redirect",
                        "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                        null, false), null);
        appnexusUsersyncer = Usersyncer.of(APPNEXUS_COOKIE,
                Usersyncer.UsersyncMethod.of("redirect",
                        "http://rubiconexample.com", null, false), null);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1);
    }

    @Test
    public void shouldLimitBidderStatusesWithAccountDefaultLimit() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(CookieSyncRequest.builder()
                .bidders(asList(RUBICON, APPNEXUS))
                .gdpr(0)
                .account("account")
                .build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(
                Account.builder()
                        .cookieSync(AccountCookieSyncConfig.of(1, null, null))
                        .build()));

        rubiconUsersyncer = Usersyncer.of(RUBICON,
                Usersyncer.UsersyncMethod.of("redirect",
                        "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}", null, false), null);
        appnexusUsersyncer = Usersyncer.of(APPNEXUS_COOKIE,
                Usersyncer.UsersyncMethod.of("redirect", "http://rubiconexample.com", null, false), null);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1);
    }

    @Test
    public void shouldLimitBidderStatusesWithAccountMaxLimit() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(CookieSyncRequest.builder()
                .bidders(asList(RUBICON, APPNEXUS))
                .gdpr(0)
                .account("account")
                .build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any())).willReturn(Future.succeededFuture(
                Account.builder()
                        .cookieSync(AccountCookieSyncConfig.of(5, 1, null))
                        .build()));

        rubiconUsersyncer = createUsersyncer(
                RUBICON,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                "redirect");
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(APPNEXUS));

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
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(liveUids).build(), jacksonMapper));

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).gdpr(0).limit(1).build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        rubiconUsersyncer = createUsersyncer(
                RUBICON,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                "redirect");
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

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
                CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).gdpr(0).limit(3).build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        rubiconUsersyncer = createUsersyncer(
                RUBICON,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                "redirect");
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://rubiconexample.com", "redirect");
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(APPNEXUS));

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
                givenRequestBody(CookieSyncRequest.builder().bidders(emptyList()).build()));

        givenTcfServiceReturningVendorIdResult(emptySet());

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).updateCookieSyncRequestMetric();
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", "redirect");
        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(set(RUBICON, APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncEvent cookieSyncEvent = captureCookieSyncTcfEvent();
        assertThat(cookieSyncEvent).isEqualTo(CookieSyncEvent.builder()
                .status(200)
                .bidderStatus(singletonList(BidderUsersyncStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of("http://adnxsexample.com", "redirect", false))
                        .build()))
                .build());
    }

    @Test
    public void shouldRespondWithNoCookieWhenBothCcpaAndGdprRejectBidders() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        given(bidderCatalog.bidderInfoByName(RUBICON)).willReturn(
                BidderInfo.create(true, null, null, null, null, null, null, 2, true, true, false));
        given(bidderCatalog.bidderInfoByName(APPNEXUS)).willReturn(
                BidderInfo.create(true, null, null, null, null, null, null, 2, true, false, false));

        given(privacyEnforcementService.isCcpaEnforced(any(), any())).willReturn(true);

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(emptySet());

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("no_cookie");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(2)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsOnly(tuple(APPNEXUS, "Rejected by TCF"), tuple(RUBICON, "Rejected by CCPA"));
    }

    private void givenTcfServiceReturningVendorIdResult(Set<Integer> vendorIds) {
        given(tcfDefinerService.resultForVendorIds(anySet(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, actions(vendorIds, false), null)));
    }

    private void givenTcfServiceReturningBlockedVendorIdResult(Set<Integer> vendorIds) {
        given(tcfDefinerService.resultForVendorIds(anySet(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, actions(vendorIds, true), null)));
    }

    private void givenTcfServiceReturningBidderNamesResult(Set<String> bidderNames) {
        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, actions(bidderNames, false), null)));
    }

    private static <T> Map<T, PrivacyEnforcementAction> actions(Set<T> keys, boolean blockPixelSync) {
        return keys.stream()
                .collect(Collectors.toMap(
                        identity(),
                        vendorId -> PrivacyEnforcementAction.builder().blockPixelSync(blockPixelSync).build()));
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

    private static Usersyncer createUsersyncer(String cookieFamilyName,
                                               String usersyncUrl,
                                               String type) {

        return Usersyncer.of(
                cookieFamilyName,
                Usersyncer.UsersyncMethod.of(type, usersyncUrl, null, false),
                null);
    }

    private CookieSyncResponse captureCookieSyncResponse() throws IOException {
        final ArgumentCaptor<String> cookieSyncResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).end(cookieSyncResponseCaptor.capture());
        return mapper.readValue(cookieSyncResponseCaptor.getValue(), CookieSyncResponse.class);
    }

    private CookieSyncEvent captureCookieSyncTcfEvent() {
        final ArgumentCaptor<CookieSyncEvent> cookieSyncEventCaptor = ArgumentCaptor.forClass(CookieSyncEvent.class);
        verify(analyticsReporterDelegator).processEvent(cookieSyncEventCaptor.capture(), any());
        return cookieSyncEventCaptor.getValue();
    }

    private CookieSyncEvent captureCookieSyncEvent() {
        final ArgumentCaptor<CookieSyncEvent> cookieSyncEventCaptor = ArgumentCaptor.forClass(CookieSyncEvent.class);
        verify(analyticsReporterDelegator).processEvent(cookieSyncEventCaptor.capture());
        return cookieSyncEventCaptor.getValue();
    }

    @SuppressWarnings("SameParameterValue")
    private static <K, V> Map<K, V> doubleMap(K key1, V value1, K key2, V value2) {
        final Map<K, V> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    @SafeVarargs
    private static <T> Set<T> set(T... elements) {
        return new HashSet<>(asList(elements));
    }
}
