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
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.gpp.CookieSyncGppService;
import org.prebid.server.cookie.CookieSyncService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.exception.CookieSyncException;
import org.prebid.server.cookie.exception.InvalidCookieSyncRequestException;
import org.prebid.server.cookie.exception.UnauthorizedUidsException;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.cookie.model.CookieSyncStatus;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.EnabledForRequestType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class CookieSyncHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private final TimeoutFactory timeoutFactory =
            new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()));

    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private CookieSyncGppService cookieSyncGppProcessor;
    @Mock
    private ActivityInfrastructureCreator activityInfrastructureCreator;
    @Mock
    private CookieSyncService cookieSyncService;
    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;
    @Mock
    private AnalyticsReporterDelegator analyticsReporterDelegator;
    @Mock
    private Metrics metrics;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerResponse httpResponse;

    private CookieSyncHandler target;

    @Before
    public void setUp() {
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        given(cookieSyncGppProcessor.updateCookieSyncRequest(any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(routingContext.response()).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.putHeader(any(CharSequence.class), any(AsciiString.class))).willReturn(httpResponse);

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(
                        Privacy.builder()
                                .gdpr("")
                                .consentString(EMPTY)
                                .ccpa(Ccpa.EMPTY)
                                .coppa(0)
                                .build(),
                        TcfContext.empty())));

        target = new CookieSyncHandler(
                500,
                0.05,
                uidsCookieService,
                cookieSyncGppProcessor,
                activityInfrastructureCreator,
                cookieSyncService,
                applicationSettings,
                privacyEnforcementService,
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
        target.handle(routingContext);

        // then
        verify(httpResponse).closed();
        verify(httpResponse).setStatusCode(400);
        verify(httpResponse).end("Invalid request format: Request has no body");
        verify(metrics).updateUserSyncBadRequestMetric();
        verifyNoMoreInteractions(httpResponse);

        final CookieSyncEvent cookieSyncEvent = captureCookieSyncEvent();
        assertThat(cookieSyncEvent)
                .isEqualTo(CookieSyncEvent.error(400, "Invalid request format: Request has no body"));
    }

    @Test
    public void shouldRespondWithErrorAndSendToAnalyticsWithoutTcfWhenRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("{"));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).closed();
        verify(httpResponse).setStatusCode(400);
        verify(httpResponse).end("Invalid request format: Request body cannot be parsed");
        verify(metrics).updateUserSyncBadRequestMetric();
        verifyNoMoreInteractions(httpResponse);

        final CookieSyncEvent cookieSyncEvent = captureCookieSyncEvent();
        assertThat(cookieSyncEvent)
                .isEqualTo(CookieSyncEvent.error(400, "Invalid request format: Request body cannot be parsed"));
    }

    @Test
    public void shouldRespondWithErrorAndSendToAnalyticsWithTcfWhenOptedOut() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(emptySet()).gdpr(1).build()));
        given(uidsCookieService.parseFromRequest(any(RoutingContext.class)))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).optout(true).build(), jacksonMapper));
        given(cookieSyncService.processContext(any()))
                .willAnswer(answerWithCookieSyncException(
                        tcfContext -> new UnauthorizedUidsException("Sync is not allowed for this uids", tcfContext)));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).closed();
        verify(httpResponse).setStatusCode(401);
        verify(httpResponse).end("Unauthorized: Sync is not allowed for this uids");
        verifyNoMoreInteractions(httpResponse);

        final CookieSyncEvent cookieSyncEvent = captureCookieSyncTcfEvent();
        assertThat(cookieSyncEvent)
                .isEqualTo(CookieSyncEvent.error(401, "Unauthorized: Sync is not allowed for this uids"));
    }

    @Test
    public void shouldRespondWithErrorIfGdprConsentIsMissing() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(emptySet()).gdpr(1).build()));
        given(cookieSyncService.processContext(any())).willAnswer(answerWithCookieSyncException(
                tcfContext -> new InvalidCookieSyncRequestException(
                        "gdpr_consent is required if gdpr is 1", tcfContext)));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).closed();
        verify(httpResponse).setStatusCode(400);
        verify(httpResponse).end("Invalid request format: gdpr_consent is required if gdpr is 1");
        verify(metrics).updateUserSyncBadRequestMetric();
        verifyNoMoreInteractions(httpResponse);

        final CookieSyncEvent cookieSyncEvent = captureCookieSyncTcfEvent();
        assertThat(cookieSyncEvent)
                .isEqualTo(CookieSyncEvent.error(400, "Invalid request format: gdpr_consent is required if gdpr is 1"));
    }

    @Test
    public void shouldRespondWithBadRequestStatusIfGdprConsentIsInvalid() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder()
                        .bidders(singleton("rubicon"))
                        .gdpr(1)
                        .gdprConsent("invalid")
                        .build()));

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(PrivacyContext.of(null,
                        TcfContext.builder().inGdprScope(true).consentValid(false).build())));

        given(cookieSyncService.processContext(any())).willAnswer(answerWithCookieSyncException(
                tcfContext -> new InvalidCookieSyncRequestException("Consent string is invalid", tcfContext)));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Consent string is invalid"));
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.builder().bidders(emptySet()).build()));

        given(routingContext.response().closed()).willReturn(true);

        given(cookieSyncService.processContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));
        given(cookieSyncService.prepareResponse(any()))
                .willReturn(CookieSyncResponse.of(CookieSyncStatus.OK, emptyList(), null));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse, never()).end();
    }

    @Test
    public void shouldRespondWithExpectedHeaders() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.builder().bidders(emptySet()).build()));

        givenDefaultCookieSyncServicePipelineResult();

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse)
                .putHeader(new AsciiString("Content-Type"), new AsciiString("application/json"));
    }

    @Test
    public void shouldPassAccountToPrivacyEnforcementServiceWhenAccountIsFound() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.builder().bidders(emptySet()).account("account").build()));

        final AccountGdprConfig accountGdprConfig = AccountGdprConfig.builder()
                .enabledForRequestType(EnabledForRequestType.of(true, true, true, true)).build();
        final Account account = Account.builder()
                .privacy(AccountPrivacyConfig.of(accountGdprConfig, null, null))
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.failedFuture("fail"));

        // when
        target.handle(routingContext);

        // then
        verify(applicationSettings).getAccountById(eq("account"), any());

        verify(privacyEnforcementService).contextFromCookieSyncRequest(any(), any(), eq(account), any());
    }

    @Test
    public void shouldPassAccountToPrivacyEnforcementServiceWhenAccountIsNotFound() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.builder().bidders(emptySet()).account("account").build()));

        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.failedFuture("bad"));

        given(privacyEnforcementService.contextFromCookieSyncRequest(any(), any(), any(), any()))
                .willReturn(Future.failedFuture("fail"));
        givenDefaultCookieSyncServicePipelineResult();

        // when
        target.handle(routingContext);

        // then
        verify(applicationSettings).getAccountById(eq("account"), any());

        verify(privacyEnforcementService)
                .contextFromCookieSyncRequest(any(), any(), eq(Account.empty("account")), any());
    }

    @Test
    public void shouldIncrementMetrics() {
        // given
        given(routingContext.getBody()).willReturn(
                givenRequestBody(CookieSyncRequest.builder().bidders(emptySet()).build()));
        givenDefaultCookieSyncServicePipelineResult();

        // when
        target.handle(routingContext);

        // then
        verify(metrics).updateCookieSyncRequestMetric();
    }

    @Test
    public void shouldPassSuccessfulEventToAnalyticsReporter() {
        // given
        given(routingContext.getBody())
                .willReturn(givenRequestBody(CookieSyncRequest.builder().bidders(singleton("rubicon")).build()));

        final List<BidderUsersyncStatus> bidderStatuses =
                singletonList(BidderUsersyncStatus.builder().bidder("rubicon").build());

        given(cookieSyncService.processContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));
        given(cookieSyncService.prepareResponse(any()))
                .willReturn(CookieSyncResponse.of(CookieSyncStatus.OK, bidderStatuses, null));

        // when
        target.handle(routingContext);

        // then
        final CookieSyncEvent cookieSyncEvent = captureCookieSyncTcfEvent();
        assertThat(cookieSyncEvent).isEqualTo(CookieSyncEvent.builder()
                .status(200)
                .bidderStatus(bidderStatuses)
                .build());
    }

    private static Buffer givenRequestBody(CookieSyncRequest request) {
        try {
            return Buffer.buffer(mapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }
    }

    private static Answer<Future<? extends Throwable>> answerWithCookieSyncException(
            Function<TcfContext, CookieSyncException> exceptionProvider) {

        return invocation -> {
            final TcfContext tcfContext = ((CookieSyncContext) invocation.getArgument(0))
                    .getPrivacyContext()
                    .getTcfContext();

            return Future.failedFuture(exceptionProvider.apply(tcfContext));
        };
    }

    private void givenDefaultCookieSyncServicePipelineResult() {
        given(cookieSyncService.processContext(any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));
        given(cookieSyncService.prepareResponse(any()))
                .willReturn(CookieSyncResponse.of(CookieSyncStatus.OK, emptyList(), null));
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
