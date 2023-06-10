package org.prebid.server.handler;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
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
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.gpp.SetuidGppService;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.model.UidsCookieUpdateResult;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.HostVendorTcfDefinerService;
import org.prebid.server.privacy.gdpr.model.HostVendorTcfResponse;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.EnabledForRequestType;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class SetuidHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String FACEBOOK = "audienceNetwork";
    private static final String ADNXS = "adnxs";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private SetuidGppService gppService;
    @Mock
    private ActivityInfrastructureCreator activityInfrastructureCreator;
    @Mock
    private HostVendorTcfDefinerService tcfDefinerService;
    @Mock
    private AnalyticsReporterDelegator analyticsReporterDelegator;
    @Mock
    private Metrics metrics;

    private SetuidHandler setuidHandler;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private ActivityInfrastructure activityInfrastructure;

    private TcfContext tcfContext;

    @Before
    public void setUp() {
        final Map<Integer, PrivacyEnforcementAction> vendorIdToGdpr = singletonMap(1,
                PrivacyEnforcementAction.allowAll());

        tcfContext = TcfContext.builder().inGdprScope(false).build();
        given(privacyEnforcementService.contextFromSetuidRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(null, tcfContext)));
        given(gppService.contextFrom(any())).willReturn(Future.succeededFuture());
        given(gppService.updateSetuidContext(any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(activityInfrastructureCreator.create(any(), any(), any()))
                .willReturn(activityInfrastructure);
        given(tcfDefinerService.resultForVendorIds(anySet(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, vendorIdToGdpr, null)));
        given(tcfDefinerService.isAllowedForHostVendorId(any()))
                .willReturn(Future.succeededFuture(HostVendorTcfResponse.allowedVendor()));
        given(tcfDefinerService.getGdprHostVendorId()).willReturn(1);

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.closed()).willReturn(false);

        given(uidsCookieService.toCookie(any())).willReturn(Cookie.cookie("test", "test"));

        given(bidderCatalog.names()).willReturn(new HashSet<>(asList("rubicon", "audienceNetwork")));
        given(bidderCatalog.isActive(any())).willReturn(true);

        given(bidderCatalog.usersyncerByName(eq(RUBICON))).willReturn(
                Optional.of(Usersyncer.of(RUBICON, null, redirectMethod())));
        given(bidderCatalog.usersyncerByName(eq(FACEBOOK))).willReturn(
                Optional.of(Usersyncer.of(FACEBOOK, null, redirectMethod())));

        given(activityInfrastructure.isAllowed(any(), any()))
                .willReturn(true);

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        setuidHandler = new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                privacyEnforcementService,
                gppService,
                activityInfrastructureCreator,
                tcfDefinerService,
                analyticsReporterDelegator,
                metrics,
                timeoutFactory);
    }

    @Test
    public void shouldRespondWithErrorAndTriggerMetricsAndAnalyticsWhenOptedOut() {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build(), jacksonMapper));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).end("Unauthorized: Sync is not allowed for this uids");
        verify(metrics).updateUserSyncOptoutMetric();

        final SetuidEvent setuidEvent = captureSetuidEvent();
        assertThat(setuidEvent).isEqualTo(SetuidEvent.error(401));
    }

    @Test
    public void shouldRespondWithErrorIfBidderParamIsMissing() {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: \"bidder\" query param is required"));
        verify(metrics).updateUserSyncBadRequestMetric();

        final SetuidEvent setuidEvent = captureSetuidEvent();
        assertThat(setuidEvent).isEqualTo(SetuidEvent.error(400));
    }

    @Test
    public void shouldRespondWithErrorIfBidderParamIsInvalid() {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(httpRequest.getParam(eq("bidder"))).willReturn("invalid_or_disabled");

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: \"bidder\" query param is invalid"));
        verify(metrics).updateUserSyncBadRequestMetric();
    }

    @Test
    public void shouldRespondWithBadRequestStatusIfGdprConsentIsInvalid() {
        // given
        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        tcfContext = TcfContext.builder().inGdprScope(true).consentValid(false).build();
        given(privacyEnforcementService.contextFromSetuidRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(null, tcfContext)));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(metrics).updateUserSyncTcfInvalidMetric(RUBICON);
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Consent string is invalid"));
    }

    @Test
    public void shouldRespondWithUnavailableForLegalReasonsStatusIfDisallowedActivity() {
        // given
        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("account")).willReturn("accountId");
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(emptyUidsCookie());
        given(applicationSettings.getAccountById(eq("accountId"), any()))
                .willReturn(Future.succeededFuture(Account.builder().build()));

        given(activityInfrastructure.isAllowed(any(), any()))
                .willReturn(false);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(451));
        verify(httpResponse).end(eq("Unavailable For Legal Reasons."));
    }

    @Test
    public void shouldPassUnsuccessfulEventToAnalyticsReporterIfUidMissingInRequest() {
        // given
        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, null))
                .willReturn(UidsCookieUpdateResult.unaltered(uidsCookie));

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
    public void shouldRespondWithoutCookieIfGdprProcessingPreventsCookieSetting() {
        // given
        final PrivacyEnforcementAction privacyEnforcementAction = PrivacyEnforcementAction.restrictAll();
        given(tcfDefinerService.resultForVendorIds(anySet(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(null, privacyEnforcementAction), null)));

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpResponse.setStatusMessage(anyString())).willReturn(httpResponse);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(routingContext, never()).addCookie(any(Cookie.class));
        verify(httpResponse).setStatusCode(eq(451));
        verify(httpResponse).end(eq("The gdpr_consent param prevents cookies from being saved"));
        verify(metrics).updateUserSyncTcfBlockedMetric(RUBICON);

        final SetuidEvent setuidEvent = captureSetuidEvent();
        assertThat(setuidEvent).isEqualTo(SetuidEvent.builder().status(451).build());
    }

    @Test
    public void shouldRespondWithBadRequestStatusIfGdprProcessingFailsWithInvalidRequestException() {
        // given
        given(tcfDefinerService.resultForVendorIds(anySet(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("gdpr exception")));

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(routingContext, never()).addCookie(any(Cookie.class));
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: gdpr exception"));

        final SetuidEvent setuidEvent = captureSetuidEvent();
        assertThat(setuidEvent).isEqualTo(SetuidEvent.error(400));
    }

    @Test
    public void shouldRespondWithInternalServerErrorStatusIfGdprProcessingFailsWithUnexpectedException() {
        // given
        given(tcfDefinerService.resultForVendorIds(anySet(), any()))
                .willReturn(Future.failedFuture("unexpected error TCF"));

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).sendFile(any());
        verify(routingContext, never()).addCookie(any(Cookie.class));
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Unexpected setuid processing error: unexpected error TCF"));
    }

    @Test
    public void shouldPassAccountToPrivacyEnforcementServiceWhenAccountIsFound() {
        // given
        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, null))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("account")).willReturn("accId");

        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabledForRequestType(EnabledForRequestType.of(true, true, true, true))
                .build();
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(accountGdprConfig, null, null))
                .build();
        final Future<Account> accountFuture = Future.succeededFuture(account);
        given(applicationSettings.getAccountById(any(), any())).willReturn(accountFuture);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(applicationSettings).getAccountById(eq("accId"), any());
        verify(privacyEnforcementService).contextFromSetuidRequest(any(), eq(account), any());
    }

    @Test
    public void shouldPassAccountToPrivacyEnforcementServiceWhenAccountIsNotFound() {
        // given
        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, null))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("account")).willReturn("accId");

        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.failedFuture("bad req"));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(applicationSettings).getAccountById(eq("accId"), any());
        verify(privacyEnforcementService).contextFromSetuidRequest(any(), eq(Account.empty("accId")), any());
    }

    @Test
    public void shouldRespondWithCookieFromRequestParam() throws IOException {
        // given
        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "J5VLCWQP-26-CWFT"))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

        // {"tempUIDs":{"rubicon":{"uid":"J5VLCWQP-26-CWFT"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJydWJpY29uIjp7InVpZCI6Iko1VkxDV1FQLTI2LUNXRlQifX19"));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(routingContext, never()).addCookie(any(Cookie.class));
        final String encodedUidsCookie = getUidsCookie();
        final Uids decodedUids = decodeUids(encodedUidsCookie);
        assertThat(decodedUids.getUids()).hasSize(1);
        assertThat(decodedUids.getUids().get(RUBICON).getUid()).isEqualTo("J5VLCWQP-26-CWFT");
    }

    @Test
    public void shouldSendPixelWhenFParamIsEqualToIWhenTypeIsIframe() {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        // {"tempUIDs":{"rubicon":{"uid":"J5VLCWQP-26-CWFT"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJydWJpY29uIjp7InVpZCI6Iko1VkxDV1FQLTI2LUNXRlQifX19"));
        given(uidsCookieService.updateUidsCookie(any(), any(), any()))
                .willReturn(UidsCookieUpdateResult.updated(emptyUidsCookie()));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("f")).willReturn("i");
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(routingContext, never()).addCookie(any(Cookie.class));
        verify(httpResponse).sendFile(any());
    }

    @Test
    public void shouldSendEmptyResponseWhenFParamIsEqualToBWhenTypeIsRedirect() {
        // given
        given(tcfDefinerService.getGdprHostVendorId()).willReturn(null);

        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "J5VLCWQP-26-CWFT"))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

        // {"tempUIDs":{"rubicon":{"uid":"J5VLCWQP-26-CWFT"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJydWJpY29uIjp7InVpZCI6Iko1VkxDV1FQLTI2LUNXRlQifX19"));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("f")).willReturn("b");
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");
        given(bidderCatalog.names()).willReturn(singleton(RUBICON));
        given(bidderCatalog.usersyncerByName(any()))
                .willReturn(Optional.of(Usersyncer.of(RUBICON, null, redirectMethod())));

        setuidHandler = new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                privacyEnforcementService,
                gppService,
                activityInfrastructureCreator,
                tcfDefinerService,
                analyticsReporterDelegator,
                metrics,
                new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(routingContext, never()).addCookie(any(Cookie.class));
        verify(httpResponse, never()).sendFile(any());
        verify(httpResponse).putHeader(eq(HttpHeaders.CONTENT_LENGTH), eq("0"));
        verify(httpResponse).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq(HttpHeaders.TEXT_HTML));
    }

    @Test
    public void shouldSendEmptyResponseWhenFParamNotDefinedAndTypeIsIframe() {
        // given
        given(tcfDefinerService.getGdprHostVendorId()).willReturn(null);

        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "J5VLCWQP-26-CWFT"))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

        // {"tempUIDs":{"rubicon":{"uid":"J5VLCWQP-26-CWFT"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJydWJpY29uIjp7InVpZCI6Iko1VkxDV1FQLTI2LUNXRlQifX19"));

        given(bidderCatalog.usersyncerByName(eq(RUBICON))).willReturn(
                Optional.of(Usersyncer.of(RUBICON, iframeMethod(), null)));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");

        setuidHandler = new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                privacyEnforcementService,
                gppService,
                activityInfrastructureCreator,
                tcfDefinerService,
                analyticsReporterDelegator,
                metrics,
                new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(routingContext, never()).addCookie(any(Cookie.class));
        verify(httpResponse, never()).sendFile(any());
        verify(httpResponse).putHeader(eq(HttpHeaders.CONTENT_LENGTH), eq("0"));
        verify(httpResponse).putHeader(eq(HttpHeaders.CONTENT_TYPE), eq(HttpHeaders.TEXT_HTML));
    }

    @Test
    public void shouldSendPixelWhenFParamNotDefinedAndTypeIsRedirect() {
        // given
        given(tcfDefinerService.getGdprHostVendorId()).willReturn(null);

        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "J5VLCWQP-26-CWFT"))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

        // {"tempUIDs":{"rubicon":{"uid":"J5VLCWQP-26-CWFT"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJydWJpY29uIjp7InVpZCI6Iko1VkxDV1FQLTI2LUNXRlQifX19"));
        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(bidderCatalog.names()).willReturn(singleton(RUBICON));
        given(bidderCatalog.usersyncerByName(any()))
                .willReturn(Optional.of(Usersyncer.of(RUBICON, null, redirectMethod())));
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");

        setuidHandler = new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                privacyEnforcementService,
                gppService,
                activityInfrastructureCreator,
                tcfDefinerService,
                analyticsReporterDelegator,
                metrics,
                new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault())));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(routingContext, never()).addCookie(any(Cookie.class));
        verify(httpResponse).sendFile(any());
    }

    @Test
    public void shouldInCookieWithRequestValue() throws IOException {
        // given
        final Map<String, UidWithExpiry> uids = Map.of(
                RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"),
                ADNXS, UidWithExpiry.live("12345"));
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(uids).build(), jacksonMapper);

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "updatedUid"))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie.updateUid(RUBICON, "updatedUid")));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("updatedUid");

        // {"tempUIDs":{"adnxs":{"uid":"12345"}, "rubicon":{"uid":"updatedUid"}}}
        given(uidsCookieService.toCookie(any()))
                .willReturn(Cookie.cookie("uids",
                        "eyJ0ZW1wVUlEcyI6eyJhZG54cyI6eyJ1aWQiOiIxMjM0NSJ9LCAicnViaWNvbiI6eyJ1aWQiOiJ1cGRhdGVkVW"
                                + "lkIn19fQ=="));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse).sendFile(any());
        verify(routingContext, never()).addCookie(any(Cookie.class));

        final String encodedUidsCookie = getUidsCookie();
        final Uids decodedUids = decodeUids(encodedUidsCookie);
        assertThat(decodedUids.getUids()).hasSize(2);
        assertThat(decodedUids.getUids().get(RUBICON).getUid()).isEqualTo("updatedUid");
        assertThat(decodedUids.getUids().get(ADNXS).getUid()).isEqualTo("12345");
    }

    @Test
    public void shouldRespondWithCookieIfUserIsNotInGdprScope() throws IOException {
        // given
        given(tcfDefinerService.resultForVendorIds(anySet(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(false, emptyMap(), null)));

        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "J5VLCWQP-26-CWFT"))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

        // {"tempUIDs":{"rubicon":{"uid":"J5VLCWQP-26-CWFT"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJydWJpY29uIjp7InVpZCI6Iko1VkxDV1FQLTI2LUNXRlQifX19"));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(routingContext, never()).addCookie(any(Cookie.class));
        verify(httpResponse).sendFile(any());

        final String encodedUidsCookie = getUidsCookie();
        final Uids decodedUids = decodeUids(encodedUidsCookie);
        assertThat(decodedUids.getUids()).hasSize(1);
        assertThat(decodedUids.getUids().get(RUBICON).getUid()).isEqualTo("J5VLCWQP-26-CWFT");
    }

    @Test
    public void shouldSkipTcfChecksAndRespondWithCookieIfHostVendorIdNotDefined() throws IOException {
        // given
        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        setuidHandler = new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                privacyEnforcementService,
                gppService,
                activityInfrastructureCreator,
                tcfDefinerService,
                analyticsReporterDelegator,
                metrics,
                new TimeoutFactory(clock));

        given(tcfDefinerService.getGdprHostVendorId()).willReturn(null);

        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "J5VLCWQP-26-CWFT"))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

        // {"tempUIDs":{"rubicon":{"uid":"J5VLCWQP-26-CWFT"}}}
        given(uidsCookieService.toCookie(any())).willReturn(Cookie
                .cookie("uids", "eyJ0ZW1wVUlEcyI6eyJydWJpY29uIjp7InVpZCI6Iko1VkxDV1FQLTI2LUNXRlQifX19"));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(tcfDefinerService, never()).resultForVendorIds(anySet(), any());
        verify(routingContext, never()).addCookie(any(Cookie.class));
        verify(httpResponse).sendFile(any());

        final String encodedUidsCookie = getUidsCookie();
        final Uids decodedUids = decodeUids(encodedUidsCookie);
        assertThat(decodedUids.getUids()).hasSize(1);
        assertThat(decodedUids.getUids().get(RUBICON).getUid()).isEqualTo("J5VLCWQP-26-CWFT");
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "uid"))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("uid");

        given(routingContext.response().closed()).willReturn(true);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse, never()).end();
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(
                Uids.builder().uids(singletonMap(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"))).build(),
                jacksonMapper);

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "updatedUid"))
                .willReturn(UidsCookieUpdateResult.updated(uidsCookie));

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

    private String getUidsCookie() {
        return httpResponse.headers().get("Set-Cookie");
    }

    private static Uids decodeUids(String value) throws IOException {
        final String uids = value.substring(5).split(";")[0];
        return mapper.readValue(Base64.getUrlDecoder().decode(uids), Uids.class);
    }

    private SetuidEvent captureSetuidEvent() {
        final ArgumentCaptor<SetuidEvent> setuidEventCaptor = ArgumentCaptor.forClass(SetuidEvent.class);
        verify(analyticsReporterDelegator).processEvent(setuidEventCaptor.capture(), eq(tcfContext));
        return setuidEventCaptor.getValue();
    }

    private static UidsCookie emptyUidsCookie() {
        return new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper);
    }

    private static UsersyncMethod iframeMethod() {
        return UsersyncMethod.builder().type(UsersyncMethodType.IFRAME).supportCORS(false).build();
    }

    private static UsersyncMethod redirectMethod() {
        return UsersyncMethod.builder().type(UsersyncMethodType.REDIRECT).supportCORS(false).build();
    }
}
