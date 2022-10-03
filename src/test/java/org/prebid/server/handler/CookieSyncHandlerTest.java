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
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
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
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.EnabledForRequestType;
import org.prebid.server.spring.config.bidder.model.CompressionType;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CookieSyncHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";
    private static final String APPNEXUS_COOKIE = "adnxs";
    public static final String NON_EXISTING_BIDDER = "nonExistingBidder";

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

        givenDefaultCookieSyncHandler();
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
                        TcfContext.builder().inGdprScope(true).consentValid(false).build())));

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
                .privacy(AccountPrivacyConfig.of(accountGdprConfig, null))
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

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", UsersyncMethodType.REDIRECT);
        rubiconUsersyncer = createUsersyncer(RUBICON, "url", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("ok",
                singletonList(BidderUsersyncStatus.builder()
                        .bidder(APPNEXUS)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of("http://adnxsexample.com", UsersyncMethodType.REDIRECT, false))
                        .build())));
    }

    @Test
    public void shouldRespondWithAllActiveBiddersWhenRequestCoopSyncTrueAndNoPriorityConfigured() throws IOException {
        // given
        final String disabledBidder = "disabled_bidder";
        given(bidderCatalog.names()).willReturn(new HashSet<>(asList(APPNEXUS, RUBICON, disabledBidder)));
        given(bidderCatalog.isActive(anyString())).willReturn(true);
        given(bidderCatalog.isActive(disabledBidder)).willReturn(false);

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(APPNEXUS)).coopSync(true).build()));

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", UsersyncMethodType.REDIRECT);
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

        givenDefaultCookieSyncHandler();

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(
                        UsersyncInfo.of("http://adnxsexample.com", UsersyncMethodType.REDIRECT, false)).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(
                        UsersyncInfo.of("http://rubiconexample.com", UsersyncMethodType.REDIRECT, false)).build())));
    }

    @Test
    public void shouldRespondWithCoopBiddersWhenRequestCoopSyncTrue() throws IOException {
        // given
        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final String disabledBidder = "disabled_bidder";
        given(bidderCatalog.isActive(disabledBidder)).willReturn(false);

        final List<Collection<String>> coopBidders = asList(singletonList(RUBICON), singletonList(disabledBidder));

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(APPNEXUS)).coopSync(true).build()));

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", UsersyncMethodType.REDIRECT);
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

        givenCookieSyncHandler("http://external-url", 2000, 100, null, 1, false, coopBidders);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(
                        UsersyncInfo.of("http://adnxsexample.com", UsersyncMethodType.REDIRECT, false)).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(
                        UsersyncInfo.of("http://rubiconexample.com", UsersyncMethodType.REDIRECT, false)).build())));
    }

    @Test
    public void shouldRespondWithCoopBiddersWhenAccountCoopSyncTrue() throws IOException {
        // given
        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final List<Collection<String>> coopBidders = singletonList(singletonList(RUBICON));

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder()
                        .bidders(singletonList(APPNEXUS))
                        .account("account")
                        .build()));

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(givenAccountWithCookieSyncConfig(null, null, true)));

        appnexusUsersyncer = Usersyncer.of(
                APPNEXUS_COOKIE,
                null,
                UsersyncMethod.builder()
                        .type(UsersyncMethodType.REDIRECT)
                        .usersyncUrl("http://adnxsexample.com")
                        .supportCORS(false)
                        .build());
        rubiconUsersyncer = Usersyncer.of(
                RUBICON,
                null,
                UsersyncMethod.builder()
                        .type(UsersyncMethodType.REDIRECT)
                        .usersyncUrl("http://rubiconexample.com")
                        .supportCORS(false)
                        .build());
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

        givenCookieSyncHandler("http://external-url", 2000, 100, null, 1, false, coopBidders);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(
                        UsersyncInfo.of("http://adnxsexample.com", UsersyncMethodType.REDIRECT, false)).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(
                        UsersyncInfo.of("http://rubiconexample.com", UsersyncMethodType.REDIRECT, false)).build())));
    }

    @Test
    public void shouldRespondWithPrioritisedCoopBidderWhenRequestCoopDefaultTrueAndLimitIsLessThanCoopSize()
            throws IOException {
        // given
        given(bidderCatalog.isActive(anyString())).willReturn(true);

        final List<Collection<String>> priorityBidders = asList(singletonList(APPNEXUS), singletonList(RUBICON),
                asList("bidder1", "bidder2"), singletonList("spam"));

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(APPNEXUS)).limit(2).build()));

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", UsersyncMethodType.REDIRECT);
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

        givenCookieSyncHandler("http://external-url", 2000, 100, null, 1, true, priorityBidders);

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(
                        UsersyncInfo.of("http://adnxsexample.com", UsersyncMethodType.REDIRECT, false)).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(
                        UsersyncInfo.of("http://rubiconexample.com", UsersyncMethodType.REDIRECT, false)).build())));
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

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", UsersyncMethodType.REDIRECT);
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

        givenDefaultCookieSyncHandler();

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie", asList(
                BidderUsersyncStatus.builder().bidder(APPNEXUS).noCookie(true).usersync(
                        UsersyncInfo.of("http://adnxsexample.com", UsersyncMethodType.REDIRECT, false)).build(),
                BidderUsersyncStatus.builder().bidder(RUBICON).noCookie(true).usersync(
                        UsersyncInfo.of("http://rubiconexample.com", UsersyncMethodType.REDIRECT, false)).build())));
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

        rubiconUsersyncer = createUsersyncer(RUBICON, "https://test.com", UsersyncMethodType.REDIRECT);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "https://test.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("ok", emptyList()));
    }

    @Test
    public void shouldReturnErrorStatusForUnsupportedBidder() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper));

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(asList(RUBICON, "unsupported")).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "https://test.com", UsersyncMethodType.REDIRECT);
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

        rubiconUsersyncer = createUsersyncer(RUBICON, "https://test.com", UsersyncMethodType.REDIRECT);
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

        rubiconUsersyncer = createUsersyncer(RUBICON, "https://test.com", UsersyncMethodType.REDIRECT);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "https://test.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        given(bidderCatalog.bidderInfoByName(APPNEXUS))
                .willReturn(BidderInfo.create(
                        true,
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        2,
                        true,
                        false,
                        CompressionType.NONE));

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
    public void shouldSkipVendorHostCheckAndContinueWithBiddersCheckWhenHostVendorIdIsMissing() throws IOException {
        // given
        final Uids uids = Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(uids, jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "https://test.com", UsersyncMethodType.REDIRECT);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "https://test.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        given(bidderCatalog.bidderInfoByName(APPNEXUS))
                .willReturn(BidderInfo.create(
                        true,
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        2,
                        true,
                        false,
                        CompressionType.NONE));

        givenTcfServiceReturningBidderNamesResult(singleton(RUBICON));
        givenCookieSyncHandler("http://external-url", 2000, 100, null, null, false, emptyList());

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
                .willReturn(BidderInfo.create(
                        true,
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        2,
                        true,
                        false,
                        CompressionType.NONE));

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

        rubiconUsersyncer = createUsersyncer(RUBICON, "url", UsersyncMethodType.IFRAME);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "url", UsersyncMethodType.IFRAME);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        verify(metrics).updateCookieSyncMatchesMetric(RUBICON);
        verify(metrics, never()).updateCookieSyncMatchesMetric(APPNEXUS);
    }

    @Test
    public void shouldRespondWithNoCookieStatusIfHostVendorRejectedByTcf() throws IOException {
        // given
        givenDefaultCookieSyncHandler();

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isActive(APPNEXUS)).willReturn(true);

        givenTcfServiceReturningBlockedVendorIdResult(Set.of(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

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

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", UsersyncMethodType.REDIRECT);
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
                        .usersync(UsersyncInfo.of("http://adnxsexample.com", UsersyncMethodType.REDIRECT, false))
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
                UsersyncMethodType.REDIRECT);
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
                UsersyncMethod.builder()
                        .type(UsersyncMethodType.IFRAME)
                        .usersyncUrl("iframe-url")
                        .supportCORS(false)
                        .build(),
                UsersyncMethod.builder()
                        .type(UsersyncMethodType.REDIRECT)
                        .usersyncUrl("redirect-url")
                        .supportCORS(false)
                        .build());
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse).isEqualTo(CookieSyncResponse.of("no_cookie",
                singletonList(BidderUsersyncStatus.builder()
                        .bidder(RUBICON)
                        .noCookie(true)
                        .usersync(UsersyncInfo.of("redirect-url", UsersyncMethodType.REDIRECT, false))
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
        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", UsersyncMethodType.REDIRECT);
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
                                + "&f=i&uid=host%2Fcookie%2Fvalue", UsersyncMethodType.REDIRECT, false));
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
                UsersyncMethodType.REDIRECT);
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
                                UsersyncMethodType.REDIRECT, false));
    }

    @Test
    public void shouldRespondWithOriginalUsersyncInfoIfNoHostCookieInRequest() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(RUBICON)).build()));

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);

        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", UsersyncMethodType.REDIRECT);
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
                .containsOnly(UsersyncInfo.of("http://rubiconexample.com", UsersyncMethodType.REDIRECT, false));
    }

    @Test
    public void shouldRespondWithOriginalUsersyncInfoIfHostCookieAndUidsAreEqual() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(singletonList(RUBICON)).build()));

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);

        rubiconUsersyncer = createUsersyncer(RUBICON, "http://rubiconexample.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

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
                .containsOnly(UsersyncInfo.of("http://rubiconexample.com", UsersyncMethodType.REDIRECT, false));
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
                UsersyncMethodType.REDIRECT);
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

        verifyNoInteractions(applicationSettings);
    }

    @Test
    public void shouldLimitAllowedBidderStatuses() throws IOException {
        // given
        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .bidders(asList(RUBICON, APPNEXUS))
                .gdpr(0)
                .limit(1)
                .build();

        given(routingContext.getBody()).willReturn(givenRequestBody(cookieSyncRequest));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(givenAccountWithCookieSyncConfig(5, 5, null)));

        givenDefaultRubiconUsersyncer();
        givenDefaultAppnexusUsersyncer();

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
    public void shouldFavourRequestAllowedBidderStatusesLimitToAccountLevelLimit() throws IOException {
        // given
        givenDefaultCookieSyncHandler();
        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .bidders(asList(RUBICON, APPNEXUS))
                .account("id")
                .gdpr(0)
                .limit(1)
                .build();

        given(routingContext.getBody()).willReturn(givenRequestBody(cookieSyncRequest));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(givenAccountWithCookieSyncConfig(2, null, null)));

        givenDefaultRubiconUsersyncer();
        givenDefaultAppnexusUsersyncer();

        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(APPNEXUS, RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1);
    }

    @Test
    public void shouldDefaultLimitToAccountLevelLimitIfAbsentInRequest() throws IOException {
        // given
        givenDefaultCookieSyncHandler();
        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .bidders(asList(RUBICON, APPNEXUS))
                .account("id")
                .gdpr(0)
                .build();

        given(routingContext.getBody()).willReturn(givenRequestBody(cookieSyncRequest));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(givenAccountWithCookieSyncConfig(2, null, null)));

        givenDefaultRubiconUsersyncer();
        givenDefaultAppnexusUsersyncer();

        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(APPNEXUS, RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(2);
    }

    @Test
    public void shouldDefaultLimitToHostLevelLimitWhenAccountLimitAndRequestLimitAreAbsent() throws IOException {
        // given
        givenDefaultCookieSyncHandlerWithDefaultLimits(2, 100);
        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .bidders(asList(RUBICON, APPNEXUS))
                .account("id")
                .gdpr(0)
                .build();

        given(routingContext.getBody()).willReturn(givenRequestBody(cookieSyncRequest));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(givenAccountWithCookieSyncConfig(null, null, null)));

        givenDefaultRubiconUsersyncer();
        givenDefaultAppnexusUsersyncer();

        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(APPNEXUS, RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(2);
    }

    @Test
    public void shouldTrimLimitToMaxValueAndLimitAllowedBidderStatuses() throws IOException {
        // given
        givenDefaultCookieSyncHandlerWithDefaultLimits(15, 100);
        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .bidders(asList(RUBICON, APPNEXUS))
                .account("id")
                .gdpr(0)
                .limit(2)
                .build();

        given(routingContext.getBody()).willReturn(givenRequestBody(cookieSyncRequest));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(givenAccountWithCookieSyncConfig(100, 1, null)));

        givenDefaultRubiconUsersyncer();
        givenDefaultAppnexusUsersyncer();

        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(APPNEXUS, RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1);
    }

    @Test
    public void shouldTrimLimitToAccountMaxLimitIfPresent() throws IOException {
        // given
        givenDefaultCookieSyncHandlerWithDefaultLimits(15, 100);
        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .bidders(asList(RUBICON, APPNEXUS))
                .account("id")
                .gdpr(0)
                .limit(2)
                .build();

        given(routingContext.getBody()).willReturn(givenRequestBody(cookieSyncRequest));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(givenAccountWithCookieSyncConfig(100, 1, null)));

        givenDefaultRubiconUsersyncer();
        givenDefaultAppnexusUsersyncer();

        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(APPNEXUS, RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1);
    }

    @Test
    public void shouldDefaultMaxLimitWhenAccountMaxLimitIsAbsent() throws IOException {
        // given
        givenDefaultCookieSyncHandlerWithDefaultLimits(15, 2);
        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .bidders(asList(RUBICON, APPNEXUS))
                .account("id")
                .gdpr(0)
                .limit(2)
                .build();

        given(routingContext.getBody()).willReturn(givenRequestBody(cookieSyncRequest));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        given(applicationSettings.getAccountById(anyString(), any()))
                .willReturn(Future.succeededFuture(givenAccountWithCookieSyncConfig(100, null, null)));

        givenDefaultRubiconUsersyncer();
        givenDefaultAppnexusUsersyncer();

        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(APPNEXUS, RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(2);
    }

    @Test
    public void shouldFavourAllowedStatusesToRejected() throws IOException {
        // given
        givenDefaultCookieSyncHandlerWithDefaultLimits(2, 100);
        final CookieSyncRequest cookieSyncRequest = CookieSyncRequest.builder()
                .bidders(asList(RUBICON, NON_EXISTING_BIDDER, APPNEXUS))
                .gdpr(0)
                .limit(2)
                .build();

        given(routingContext.getBody()).willReturn(givenRequestBody(cookieSyncRequest));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        givenDefaultRubiconUsersyncer();
        givenDefaultAppnexusUsersyncer();

        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(APPNEXUS, RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getBidderStatus())
                .extracting(BidderUsersyncStatus::getBidder)
                .containsExactlyInAnyOrder(APPNEXUS, RUBICON);
    }

    @Test
    public void shouldLimitBidderStatusesWithLiveUids() throws IOException {
        // given
        Map<String, UidWithExpiry> liveUids = Map.of(
                RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"),
                APPNEXUS_COOKIE, UidWithExpiry.live("1234567890"));

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(liveUids).build(), jacksonMapper));

        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder().bidders(asList(RUBICON, APPNEXUS)).gdpr(0).limit(1).build()));

        given(bidderCatalog.isActive(anyString())).willReturn(true);

        rubiconUsersyncer = createUsersyncer(
                RUBICON,
                "http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}",
                UsersyncMethodType.REDIRECT);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://rubiconexample.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

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
                UsersyncMethodType.REDIRECT);
        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://rubiconexample.com", UsersyncMethodType.REDIRECT);
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

        appnexusUsersyncer = createUsersyncer(APPNEXUS_COOKIE, "http://adnxsexample.com", UsersyncMethodType.REDIRECT);
        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        givenUsersyncersReturningFamilyName();

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(Set.of(RUBICON, APPNEXUS));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final BidderUsersyncStatus expectedAppnexusStatus = BidderUsersyncStatus.builder()
                .bidder(APPNEXUS)
                .noCookie(true)
                .usersync(UsersyncInfo.of("http://adnxsexample.com", UsersyncMethodType.REDIRECT, false))
                .build();

        final CookieSyncEvent cookieSyncEvent = captureCookieSyncTcfEvent();
        assertThat(cookieSyncEvent).isEqualTo(CookieSyncEvent.builder()
                .status(200)
                .bidderStatus(List.of(expectedAppnexusStatus, unconfiguredBidderStatus(RUBICON)))
                .build());
    }

    @Test
    public void shouldRespondWithNoCookieWhenCcpaRejectsBidder() throws IOException {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class))).willReturn(new UidsCookie(
                Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(singletonList(RUBICON)).build()));

        rubiconUsersyncer = createUsersyncer(RUBICON, "", null);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.bidderInfoByName(RUBICON)).willReturn(
                BidderInfo.create(
                        true,
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        2,
                        true,
                        false,
                        CompressionType.NONE));

        given(privacyEnforcementService.isCcpaEnforced(any(), any())).willReturn(true);

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(emptySet());

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("no_cookie");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(1)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsOnly(tuple(RUBICON, "Rejected by CCPA"));
    }

    @Test
    public void shouldReturnErrorStatusForUnsupportedBidderInCcpaContext() throws IOException {
        // given
        given(routingContext.getBody()).willReturn(givenRequestBody(
                CookieSyncRequest.builder()
                        .bidders(asList(RUBICON, NON_EXISTING_BIDDER))
                        .usPrivacy("1YYY")
                        .build()));

        given(privacyEnforcementService.isCcpaEnforced(any(), any()))
                .willReturn(true);

        given(bidderCatalog.bidderInfoByName(NON_EXISTING_BIDDER))
                .willReturn(null);

        rubiconUsersyncer = createUsersyncer(RUBICON, "https://test.com", UsersyncMethodType.REDIRECT);
        givenUsersyncersReturningFamilyName();

        given(bidderCatalog.isActive(RUBICON)).willReturn(true);
        given(bidderCatalog.isValidName(NON_EXISTING_BIDDER)).willReturn(false);

        givenTcfServiceReturningVendorIdResult(singleton(1));
        givenTcfServiceReturningBidderNamesResult(singleton(RUBICON));

        // when
        cookieSyncHandler.handle(routingContext);

        // then
        final CookieSyncResponse cookieSyncResponse = captureCookieSyncResponse();
        assertThat(cookieSyncResponse.getStatus()).isEqualTo("no_cookie");
        assertThat(cookieSyncResponse.getBidderStatus()).hasSize(2)
                .extracting(BidderUsersyncStatus::getBidder, BidderUsersyncStatus::getError)
                .containsExactlyInAnyOrder(tuple(NON_EXISTING_BIDDER, "Unsupported bidder"),
                        tuple(RUBICON, null));
    }

    private void givenCookieSyncHandler(String externalUrl,
                                        int defaultTimeout,
                                        Integer defaultLimit,
                                        Integer defaultMaxLimit,
                                        Integer gdprHostVendorId,
                                        boolean defaultCoopSync,
                                        List<Collection<String>> listOfCoopSyncBidders) {

        cookieSyncHandler = new CookieSyncHandler(
                externalUrl,
                defaultTimeout,
                defaultLimit,
                defaultMaxLimit,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                tcfDefinerService,
                privacyEnforcementService,
                gdprHostVendorId,
                defaultCoopSync,
                listOfCoopSyncBidders,
                analyticsReporterDelegator,
                metrics,
                timeoutFactory,
                jacksonMapper);
    }

    private void givenDefaultCookieSyncHandler() {
        givenCookieSyncHandler("http://external-url", 2000, 100, 100, 1, false, emptyList());
    }

    private void givenDefaultCookieSyncHandlerWithDefaultLimits(Integer defaultLimit, Integer defaultMaxLimit) {
        givenCookieSyncHandler("http://external-url", 2000, defaultLimit, defaultMaxLimit, 1, false, emptyList());
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

    private static Account givenAccountWithCookieSyncConfig(Integer defaultLimit,
                                                            Integer maxLimit,
                                                            Boolean defaultCoopSync) {

        return Account.builder()
                .cookieSync(AccountCookieSyncConfig.of(defaultLimit, maxLimit, defaultCoopSync))
                .build();
    }

    private void givenUsersyncersReturningFamilyName() {
        given(bidderCatalog.isValidName(RUBICON)).willReturn(true);
        given(bidderCatalog.usersyncerByName(RUBICON)).willReturn(Optional.ofNullable(rubiconUsersyncer));

        given(bidderCatalog.isValidName(APPNEXUS)).willReturn(true);
        given(bidderCatalog.usersyncerByName(APPNEXUS)).willReturn(Optional.ofNullable(appnexusUsersyncer));
    }

    private void givenDefaultRubiconUsersyncer() {
        rubiconUsersyncer = Usersyncer.of(
                RUBICON,
                null,
                UsersyncMethod.builder()
                        .type(UsersyncMethodType.REDIRECT)
                        .usersyncUrl("http://adnxsexample.com/sync?gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}")
                        .supportCORS(false)
                        .build());
    }

    private void givenDefaultAppnexusUsersyncer() {
        appnexusUsersyncer = Usersyncer.of(
                APPNEXUS_COOKIE,
                null,
                UsersyncMethod.builder()
                        .type(UsersyncMethodType.REDIRECT)
                        .usersyncUrl("http://rubiconexample.com")
                        .supportCORS(false)
                        .build());
    }

    private static BidderUsersyncStatus unconfiguredBidderStatus(String bidderName) {
        return BidderUsersyncStatus.builder()
                .bidder(bidderName)
                .error(bidderName + " is requested for syncing, but doesn't have appropriate sync method")
                .build();
    }

    private static Usersyncer createUsersyncer(String cookieFamilyName, String usersyncUrl, UsersyncMethodType type) {
        if (type == null) {
            return Usersyncer.of(cookieFamilyName, null, null);
        }

        final UsersyncMethod usersyncMethod = UsersyncMethod.builder()
                .type(type)
                .usersyncUrl(usersyncUrl)
                .supportCORS(false)
                .build();

        return switch (type) {
            case REDIRECT -> Usersyncer.of(cookieFamilyName, null, usersyncMethod);
            case IFRAME -> Usersyncer.of(cookieFamilyName, usersyncMethod, null);
        };
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
}
