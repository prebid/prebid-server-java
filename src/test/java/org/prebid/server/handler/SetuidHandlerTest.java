package org.prebid.server.handler;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.gpp.SetuidGppService;
import org.prebid.server.auction.privacy.contextfactory.SetuidPrivacyContextFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.exception.InvalidAccountConfigException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.timeout.TimeoutFactory;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.prebid.server.model.UpdateResult.unaltered;
import static org.prebid.server.model.UpdateResult.updated;

@ExtendWith(MockitoExtension.class)
public class SetuidHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String FACEBOOK = "audienceNetwork";
    private static final String ADNXS = "adnxs";
    private static final String APPNEXUS = "appnexus";

    @Mock(strictness = LENIENT)
    private UidsCookieService uidsCookieService;
    @Mock(strictness = LENIENT)
    private ApplicationSettings applicationSettings;
    @Mock(strictness = LENIENT)
    private BidderCatalog bidderCatalog;
    @Mock(strictness = LENIENT)
    private SetuidPrivacyContextFactory setuidPrivacyContextFactory;
    @Mock(strictness = LENIENT)
    private SetuidGppService gppService;
    @Mock(strictness = LENIENT)
    private ActivityInfrastructureCreator activityInfrastructureCreator;
    @Mock(strictness = LENIENT)
    private HostVendorTcfDefinerService tcfDefinerService;
    @Mock
    private AnalyticsReporterDelegator analyticsReporterDelegator;
    @Mock
    private Metrics metrics;

    private SetuidHandler setuidHandler;

    @Mock(strictness = LENIENT)
    private RoutingContext routingContext;
    @Mock(strictness = LENIENT)
    private HttpServerRequest httpRequest;
    @Mock(strictness = LENIENT)
    private HttpServerResponse httpResponse;
    @Mock(strictness = LENIENT)
    private ActivityInfrastructure activityInfrastructure;

    private TcfContext tcfContext;

    @BeforeEach
    public void setUp() {
        final Map<String, PrivacyEnforcementAction> bidderToGdpr = Map.of(
                RUBICON, PrivacyEnforcementAction.allowAll(),
                APPNEXUS, PrivacyEnforcementAction.allowAll(),
                FACEBOOK, PrivacyEnforcementAction.allowAll());

        tcfContext = TcfContext.builder().inGdprScope(false).build();
        given(setuidPrivacyContextFactory.contextFrom(any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(null, tcfContext)));
        given(gppService.contextFrom(any())).willReturn(Future.succeededFuture());
        given(gppService.updateSetuidContext(any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(activityInfrastructureCreator.create(any(), any(), any()))
                .willReturn(activityInfrastructure);
        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any()))
                .willReturn(Future.succeededFuture(TcfResponse.of(true, bidderToGdpr, null)));
        given(tcfDefinerService.isAllowedForHostVendorId(any()))
                .willReturn(Future.succeededFuture(HostVendorTcfResponse.allowedVendor()));
        given(tcfDefinerService.getGdprHostVendorId()).willReturn(1);

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpResponse.putHeader(any(CharSequence.class), any(CharSequence.class))).willReturn(httpResponse);
        given(httpResponse.closed()).willReturn(false);

        given(uidsCookieService.splitUidsIntoCookies(any())).willAnswer(invocation -> singletonList(
                Cookie.cookie(
                        "uids",
                        Base64.getUrlEncoder().encodeToString(((UidsCookie) invocation.getArgument(0))
                                .toJson().getBytes()))));

        given(bidderCatalog.usersyncReadyBidders()).willReturn(Set.of(RUBICON, FACEBOOK, APPNEXUS));
        given(bidderCatalog.isAlias(any())).willReturn(false);

        given(bidderCatalog.usersyncerByName(eq(RUBICON))).willReturn(
                Optional.of(Usersyncer.of(RUBICON, null, redirectMethod(), false, null)));
        given(bidderCatalog.cookieFamilyName(eq(RUBICON))).willReturn(Optional.of(RUBICON));

        given(bidderCatalog.usersyncerByName(eq(FACEBOOK))).willReturn(
                Optional.of(Usersyncer.of(FACEBOOK, null, redirectMethod(), false, null)));
        given(bidderCatalog.cookieFamilyName(eq(FACEBOOK))).willReturn(Optional.of(FACEBOOK));

        given(bidderCatalog.usersyncerByName(eq(APPNEXUS))).willReturn(
                Optional.of(Usersyncer.of(ADNXS, null, redirectMethod(), false, null)));
        given(bidderCatalog.cookieFamilyName(eq(APPNEXUS))).willReturn(Optional.of(ADNXS));

        given(activityInfrastructure.isAllowed(any(), any()))
                .willReturn(true);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder().build()));

        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
        setuidHandler = new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                setuidPrivacyContextFactory,
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
        given(setuidPrivacyContextFactory.contextFrom(any(), any(), any()))
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
    public void shouldRespondWithErrorOnInvalidAccountConfigException() {
        // given
        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("account")).willReturn("accountId");
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(emptyUidsCookie());
        given(applicationSettings.getAccountById(eq("accountId"), any()))
                .willReturn(Future.succeededFuture(Account.builder().build()));

        given(gppService.contextFrom(any()))
                .willReturn(Future.failedFuture(new InvalidAccountConfigException("Message")));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid account configuration: Message"));
    }

    @Test
    public void shouldPassUnsuccessfulEventToAnalyticsReporterIfUidMissingInRequest() {
        // given
        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, null))
                .willReturn(unaltered(uidsCookie));

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
        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any()))
                .willReturn(Future.succeededFuture(
                        TcfResponse.of(true, singletonMap(RUBICON, privacyEnforcementAction), null)));

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
        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any()))
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
        given(tcfDefinerService.resultForBidderNames(anySet(), any(), any()))
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
                .willReturn(updated(uidsCookie));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("account")).willReturn("accId");

        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabledForRequestType(EnabledForRequestType.of(true, true, true, true, true))
                .build();
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.builder().gdpr(accountGdprConfig).build())
                .build();
        final Future<Account> accountFuture = Future.succeededFuture(account);
        given(applicationSettings.getAccountById(any(), any())).willReturn(accountFuture);

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(applicationSettings).getAccountById(eq("accId"), any());
        verify(setuidPrivacyContextFactory).contextFrom(any(), eq(account), any());
    }

    @Test
    public void shouldPassAccountToPrivacyEnforcementServiceWhenAccountIsNotFound() {
        // given
        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, null))
                .willReturn(updated(uidsCookie));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("account")).willReturn("accId");

        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.failedFuture("bad req"));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(applicationSettings).getAccountById(eq("accId"), any());
        verify(setuidPrivacyContextFactory).contextFrom(any(), eq(Account.empty("accId")), any());
    }

    @Test
    public void shouldRespondWithCookieFromRequestParam() throws IOException {
        // given
        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);

        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "J5VLCWQP-26-CWFT"))
                .willReturn(updated(uidsCookie.updateUid(RUBICON, "J5VLCWQP-26-CWFT")));

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
    public void shouldRespondWithCookieFromRequestParamWhenBidderAndCookieFamilyAreDifferent() throws IOException {
        // given
        final UidsCookie uidsCookie = emptyUidsCookie();
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        given(uidsCookieService.updateUidsCookie(uidsCookie, ADNXS, "J5VLCWQP-26-CWFT"))
                .willReturn(updated(uidsCookie.updateUid(ADNXS, "J5VLCWQP-26-CWFT")));

        given(httpRequest.getParam("bidder")).willReturn(ADNXS);
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(routingContext, never()).addCookie(any(Cookie.class));
        final String encodedUidsCookie = getUidsCookie();
        final Uids decodedUids = decodeUids(encodedUidsCookie);
        assertThat(decodedUids.getUids()).hasSize(1);
        assertThat(decodedUids.getUids().get(ADNXS).getUid()).isEqualTo("J5VLCWQP-26-CWFT");
    }

    @Test
    public void shouldSendPixelWhenFParamIsEqualToIWhenTypeIsIframe() {
        // given
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(uidsCookieService.updateUidsCookie(any(), any(), any()))
                .willReturn(updated(emptyUidsCookie()));

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
                .willReturn(updated(uidsCookie));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("f")).willReturn("b");
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");
        given(bidderCatalog.usersyncReadyBidders()).willReturn(singleton(RUBICON));
        given(bidderCatalog.usersyncerByName(any()))
                .willReturn(Optional.of(Usersyncer.of(RUBICON, null, redirectMethod(), false, null)));

        setuidHandler = new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                setuidPrivacyContextFactory,
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
                .willReturn(updated(uidsCookie));

        given(bidderCatalog.usersyncerByName(eq(RUBICON))).willReturn(
                Optional.of(Usersyncer.of(RUBICON, iframeMethod(), null, false, null)));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");

        setuidHandler = new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                setuidPrivacyContextFactory,
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
                .willReturn(updated(uidsCookie));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(bidderCatalog.usersyncReadyBidders()).willReturn(singleton(RUBICON));
        given(bidderCatalog.usersyncerByName(any()))
                .willReturn(Optional.of(Usersyncer.of(RUBICON, null, redirectMethod(), false, null)));
        given(httpRequest.getParam("uid")).willReturn("J5VLCWQP-26-CWFT");

        setuidHandler = new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                setuidPrivacyContextFactory,
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
                .willReturn(updated(uidsCookie.updateUid(RUBICON, "updatedUid")));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("updatedUid");

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
    public void shouldReturnMultipleCookies() throws IOException {
        // given
        final Map<String, UidWithExpiry> uids = Map.of(
                RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"),
                ADNXS, UidWithExpiry.live("12345"));
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(uids).build(), jacksonMapper);

        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(uidsCookie);
        final UidsCookie givenUidsCookie = uidsCookie
                .updateUid(RUBICON, "updatedUid")
                .updateUid(ADNXS, "12345");

        given(uidsCookieService.updateUidsCookie(uidsCookie, RUBICON, "updatedUid"))
                .willReturn(updated(givenUidsCookie));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("updatedUid");

        // {"tempUIDs":{"adnxs":{"uid":"12345"}, "rubicon":{"uid":"updatedUid"}}}
        given(uidsCookieService.splitUidsIntoCookies(givenUidsCookie)).willReturn(List.of(
                Cookie.cookie("uids", "eyJ0ZW1wVUlEcyI6eyJydWJpY29uIjp7InVpZCI6InVwZGF0ZWRVaWQifX19"),
                Cookie.cookie("uids2", "eyJ0ZW1wVUlEcyI6eyJhZG54cyI6eyJ1aWQiOiIxMjM0NSJ9fX0")));

        // when
        setuidHandler.handle(routingContext);

        // then
        verify(httpResponse).sendFile(any());
        verify(routingContext, never()).addCookie(any(Cookie.class));

        final Map<String, String> encodedUidsCookie = httpResponse.headers().getAll("Set-Cookie").stream()
                .collect(Collectors.toMap(value -> value.split("=")[0], value -> value.split("=")[1]));

        assertThat(encodedUidsCookie).hasSize(2);
        final Uids decodedUids1 = mapper.readValue(Base64.getUrlDecoder()
                .decode(encodedUidsCookie.get("uids")), Uids.class);
        final Uids decodedUids2 = mapper.readValue(Base64.getUrlDecoder()
                .decode(encodedUidsCookie.get("uids2")), Uids.class);

        assertThat(decodedUids1.getUids()).hasSize(1);
        assertThat(decodedUids1.getUids().get(RUBICON).getUid()).isEqualTo("updatedUid");

        assertThat(decodedUids2.getUids()).hasSize(1);
        assertThat(decodedUids2.getUids().get(ADNXS).getUid()).isEqualTo("12345");
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
                .willReturn(updated(uidsCookie.updateUid(RUBICON, "J5VLCWQP-26-CWFT")));

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
                setuidPrivacyContextFactory,
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
                .willReturn(updated(uidsCookie.updateUid(RUBICON, "J5VLCWQP-26-CWFT")));

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
                .willReturn(updated(uidsCookie));

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
                .willReturn(updated(uidsCookie));

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

    @Test
    public void shouldThrowExceptionInCaseOfBaseBidderCookieFamilyNameDuplicates() {
        // given
        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        final String firstDuplicateName = "firstBidderWithDuplicate";
        final String secondDuplicateName = "secondBidderWithDuplicate";
        final String thirdDuplicateName = "thirdDuplicateName";

        given(bidderCatalog.usersyncReadyBidders())
                .willReturn(Set.of(RUBICON, FACEBOOK, firstDuplicateName, secondDuplicateName, thirdDuplicateName));
        given(bidderCatalog.isAlias(thirdDuplicateName)).willReturn(true);
        given(bidderCatalog.usersyncerByName(eq(firstDuplicateName))).willReturn(
                Optional.of(Usersyncer.of(RUBICON, iframeMethod(), redirectMethod(), false, null)));
        given(bidderCatalog.usersyncerByName(eq(secondDuplicateName))).willReturn(
                Optional.of(Usersyncer.of(FACEBOOK, iframeMethod(), redirectMethod(), false, null)));
        given(bidderCatalog.usersyncerByName(eq(thirdDuplicateName))).willReturn(
                Optional.of(Usersyncer.of(FACEBOOK, iframeMethod(), redirectMethod(), false, null)));

        final Executable exceptionSource = () -> new SetuidHandler(
                2000,
                uidsCookieService,
                applicationSettings,
                bidderCatalog,
                setuidPrivacyContextFactory,
                gppService,
                activityInfrastructureCreator,
                tcfDefinerService,
                analyticsReporterDelegator,
                metrics,
                new TimeoutFactory(clock));

        //when
        final IllegalArgumentException exception =
                Assertions.assertThrows(IllegalArgumentException.class, exceptionSource);

        //then
        final String expectedPrefix = "Duplicated \"cookie-family-name\" found, values: ";
        final String actualMessage = exception.getMessage();

        assertThat(actualMessage).startsWith(expectedPrefix);

        final String[] values = actualMessage.substring(expectedPrefix.length()).split(", ");
        assertThat(values).containsExactlyInAnyOrder("audienceNetwork", "rubicon");
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
