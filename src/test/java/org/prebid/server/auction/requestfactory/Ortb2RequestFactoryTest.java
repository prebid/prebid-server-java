package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionStoredResult;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.floors.PriceFloorProcessor;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsaTransparency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountDsaConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.settings.model.AccountTargetingConfig;
import org.prebid.server.settings.model.DefaultDsa;
import org.prebid.server.settings.model.DsaTransparency;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class Ortb2RequestFactoryTest extends VertxTest {

    private static final List<String> BLACKLISTED_ACCOUNTS = singletonList("bad_acc");

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private ActivityInfrastructureCreator activityInfrastructureCreator;
    @Mock
    private RequestValidator requestValidator;
    @Mock
    private TimeoutResolver timeoutResolver;
    @Mock
    private TimeoutFactory timeoutFactory;
    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private IpAddressHelper ipAddressHelper;
    @Mock
    private HookStageExecutor hookStageExecutor;
    @Mock
    private PriceFloorProcessor priceFloorProcessor;
    @Mock
    private CountryCodeMapper countryCodeMapper;
    @Mock
    private Metrics metrics;

    private Ortb2RequestFactory target;

    @Mock
    private Timeout timeout;

    private HttpRequestContext httpRequest;
    private HookExecutionContext hookExecutionContext;

    @Before
    public void setUp() {
        httpRequest = HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.empty())
                .build();
        hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        given(timeoutResolver.limitToMax(any())).willReturn(2000L);

        given(hookStageExecutor.executeEntrypointStage(any(), any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        EntrypointPayloadImpl.of(
                                invocation.getArgument(0),
                                invocation.getArgument(1),
                                invocation.getArgument(2)))));

        given(hookStageExecutor.executeRawAuctionRequestStage(any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        AuctionRequestPayloadImpl.of(invocation.getArgument(0)))));

        given(hookStageExecutor.executeProcessedAuctionRequestStage(any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        AuctionRequestPayloadImpl.of(invocation.getArgument(0)))));

        givenTarget(90);
    }

    @Test
    public void creationShouldFailOnNegativeTimeoutAdjustmentFactor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> givenTarget(-1))
                .withMessage("Expected timeout adjustment factor should be in [0, 100].");
    }

    @Test
    public void shouldIncrementRejectedByInvalidAccountMetricsIfUnknownUser() {
        // given
        givenTarget(90);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        final String accountId = "absentId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .app(App.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        // when
        target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(metrics).updateAccountRequestRejectedByInvalidAccountMetrics(eq("absentId"));
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfAccountIsEnforcedAndFailedGetAccountById() {
        // given
        givenTarget(90);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        final String accountId = "absentId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .app(App.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        // when
        final Future<?> future = target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Unauthorized account id: absentId");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfAccountIsInactive() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .app(App.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .id(accountId)
                        .status(AccountStatus.inactive)
                        .build()));

        // when
        final Future<?> future = target.fetchAccount(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Account accountId is inactive");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureWhenAccountIdIsBlacklisted() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id("bad_acc").build())
                        .build()));

        // when
        final Future<?> result = target.fetchAccount(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(BlacklistedAccountException.class)
                .hasMessage("Prebid-server has blacklisted Account ID: bad_acc, please reach out to the prebid "
                        + "server host.");
    }

    @Test
    public void fetchAccountShouldReturnAccountWithAccountIdTakenFromPublisherExt() {
        // given
        final String parentAccount = "parentAccount";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(ExtPublisher.of(ExtPublisherPrebid.of(parentAccount)))
                                .build())
                        .build()));

        final Account account = Account.builder()
                .id(parentAccount)
                .auction(AccountAuctionConfig.builder().build())
                .build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(applicationSettings).getAccountById(eq(parentAccount), any());

        assertThat(result.result()).isSameAs(account);
    }

    @Test
    public void fetchAccountShouldReturnAccountWithAccountIdTakenFromPublisherIdWhenExtIsNull() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).ext(null).build())
                        .build()));

        final Account account = Account.builder().id(accountId).auction(AccountAuctionConfig.builder().build()).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result()).isSameAs(account);
    }

    @Test
    public void fetchAccountShouldReturnAccountWithAccountIdTakenFromPublisherIdWhenExtPublisherPrebidIsNull() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId)
                                .ext(ExtPublisher.empty())
                                .build())
                        .build()));

        final Account account = Account.builder().id(accountId).auction(AccountAuctionConfig.builder().build()).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result()).isSameAs(account);
    }

    @Test
    public void fetchAccountShouldReturnAccountWithAccountIdTakenFromPublisherIdWhenExtParentIsEmpty() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(ExtPublisher.of(ExtPublisherPrebid.of("")))
                                .build())
                        .build()));

        final Account account = Account.builder()
                .id("accountId")
                .auction(AccountAuctionConfig.builder().build())
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(applicationSettings).getAccountById(eq("accountId"), any());

        assertThat(result.result()).isSameAs(account);
    }

    @Test
    public void fetchAccountShouldReturnAccountWithAccountIdTakenFromAppPublisherId() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .build();

        final Account account = Account.builder().id(accountId).auction(AccountAuctionConfig.builder().build()).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result()).isSameAs(account);
    }

    @Test
    public void fetchAccountShouldReturnAccountWithAccountIdTakenFromDoohPublisherId() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = BidRequest.builder()
                .dooh(Dooh.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .build();

        final Account account = Account.builder().id(accountId).auction(AccountAuctionConfig.builder().build()).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result()).isSameAs(account);
    }

    @Test
    public void shouldFetchAccountFromStoredIfStoredLookupIsTrueAndAccountIsNotFoundPreviously() {
        // given
        final BidRequest receivedBidRequest = givenBidRequest(identity());

        final String accountId = "accountId";
        final BidRequest mergedBidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        given(storedRequestProcessor.processAuctionRequest(any(), any()))
                .willReturn(Future.succeededFuture(AuctionStoredResult.of(false, mergedBidRequest)));

        final Account fetchedAccount = Account.builder().id(accountId).status(AccountStatus.active).build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(fetchedAccount));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(receivedBidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(storedRequestProcessor).processAuctionRequest("", receivedBidRequest);
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result()).isEqualTo(fetchedAccount);
    }

    @Test
    public void shouldFetchAccountFromStoredAndReturnFailedFutureWhenAccountIdIsBlacklisted() {
        // given
        final BidRequest receivedBidRequest = givenBidRequest(identity());

        final BidRequest mergedBidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id("bad_acc").build()).build()));

        given(storedRequestProcessor.processAuctionRequest(any(), any()))
                .willReturn(Future.succeededFuture(AuctionStoredResult.of(false, mergedBidRequest)));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(receivedBidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(storedRequestProcessor).processAuctionRequest("", receivedBidRequest);
        verifyNoInteractions(applicationSettings);

        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(BlacklistedAccountException.class)
                .hasMessage("Prebid-server has blacklisted Account ID: bad_acc, please reach out to the prebid "
                        + "server host.");
    }

    @Test
    public void shouldFetchAccountFromStoredAndReturnFailedFutureIfStoredLookupIsFailed() {
        // given
        givenTarget(90);

        final BidRequest receivedBidRequest = givenBidRequest(identity());
        given(storedRequestProcessor.processAuctionRequest(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(receivedBidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(storedRequestProcessor).processAuctionRequest("", receivedBidRequest);
        verifyNoInteractions(applicationSettings);

        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessage("error");
    }

    @Test
    public void shouldFetchAccountFromStoredAndReturnEmptyAccountIfStoredLookupIsFailed() {
        // given
        final BidRequest receivedBidRequest = givenBidRequest(identity());
        given(storedRequestProcessor.processAuctionRequest(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(receivedBidRequest)
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verify(storedRequestProcessor).processAuctionRequest("", receivedBidRequest);
        verifyNoInteractions(applicationSettings);

        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessage("error");
    }

    @Test
    public void fetchAccountWithoutStoredRequestLookupShouldNeverCallStoredProcessor() {
        // when
        target.fetchAccountWithoutStoredRequestLookup(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(givenBidRequest(identity()))
                        .timeoutContext(TimeoutContext.of(0, null, 0))
                        .build());

        // then
        verifyNoInteractions(storedRequestProcessor);
    }

    @Test
    public void createAuctionContextShouldReturnExpectedAuctionContext() {
        // when
        final AuctionContext result = target.createAuctionContext(Endpoint.openrtb2_auction, MetricName.openrtb2app);

        // then
        assertThat(result).isEqualTo(AuctionContext.builder()
                .requestTypeMetric(MetricName.openrtb2app)
                .prebidErrors(new ArrayList<>())
                .debugWarnings(new ArrayList<>())
                .hookExecutionContext(hookExecutionContext)
                .debugContext(DebugContext.empty())
                .requestRejected(false)
                .debugHttpCalls(emptyMap())
                .bidRejectionTrackers(new HashMap<>())
                .build());
    }

    @Test
    public void enrichAuctionContextShouldReturnExpectedAuctionContext() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .tmax(1000L)
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .debug(1)
                        .trace(TraceLevel.basic)
                        .build()))
                .build();

        final long resolvedTimeout = 200L;
        given(timeoutResolver.limitToMax(anyLong())).willReturn(resolvedTimeout);
        given(timeoutFactory.create(anyLong(), anyLong())).willReturn(timeout);

        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper);
        given(uidsCookieService.parseFromRequest(any(HttpRequestContext.class))).willReturn(uidsCookie);

        // when
        final AuctionContext result = target.enrichAuctionContext(
                AuctionContext.builder()
                        .requestTypeMetric(MetricName.openrtb2app)
                        .prebidErrors(new ArrayList<>())
                        .debugWarnings(new ArrayList<>())
                        .hookExecutionContext(hookExecutionContext)
                        .debugHttpCalls(emptyMap())
                        .build(),
                httpRequest,
                bidRequest,
                100);

        // then
        verify(timeoutResolver).limitToMax(1000L);
        verify(timeoutFactory).create(100, resolvedTimeout);

        verify(uidsCookieService).parseFromRequest(httpRequest);

        assertThat(result).usingRecursiveComparison().isEqualTo(AuctionContext.builder()
                .httpRequest(httpRequest)
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .requestTypeMetric(MetricName.openrtb2app)
                .timeoutContext(TimeoutContext.of(100L, timeout, 90))
                .prebidErrors(new ArrayList<>())
                .debugWarnings(new ArrayList<>())
                .hookExecutionContext(hookExecutionContext)
                .debugHttpCalls(new HashMap<>())
                .build());
    }

    @Test
    public void enrichAuctionContextShouldSetDebugOff() {
        // when
        final AuctionContext result = target.enrichAuctionContext(
                AuctionContext.builder()
                        .debugContext(DebugContext.empty())
                        .build(),
                httpRequest,
                BidRequest.builder().build(),
                100);

        // then
        assertThat(result.getDebugContext()).isEqualTo(DebugContext.empty());
    }

    @Test
    public void validateRequestShouldThrowInvalidRequestExceptionIfRequestIsInvalid() {
        // given
        given(requestValidator.validate(any(), any())).willReturn(ValidationResult.error("error"));

        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Future<BidRequest> result = target.validateRequest(
                bidRequest,
                HttpRequestContext.builder().build(),
                new ArrayList<>());

        // then
        assertThat(result).isFailed();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");

        verify(requestValidator).validate(eq(bidRequest), any());
    }

    @Test
    public void validateRequestShouldReturnSameBidRequest() {
        // given
        given(requestValidator.validate(any(), any())).willReturn(ValidationResult.success());

        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final BidRequest result = target.validateRequest(
                bidRequest,
                HttpRequestContext.builder().build(),
                new ArrayList<>()).result();

        // then
        verify(requestValidator).validate(eq(bidRequest), any());

        assertThat(result).isSameAs(bidRequest);
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldReturnSameBidRequest() {
        // given
        final String accountId = "accId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(emptyList())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .integration("integration")
                        .targeting(ExtRequestTargeting.builder()
                                .includewinners(true)
                                .includebidderkeys(true)
                                .includeformat(true)
                                .preferdeals(true)
                                .alwaysincludedeals(true)
                                .build())
                        .build())));

        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.empty(),
                "ip");

        final Account account = Account.builder()
                .id(accountId)
                .auction(AccountAuctionConfig.builder()
                        .defaultIntegration("accountIntegration")
                        .targeting(AccountTargetingConfig.builder()
                                .includeWinners(false)
                                .includeBidderKeys(false)
                                .includeFormat(false)
                                .preferDeals(false)
                                .alwaysIncludeDeals(false)
                                .build())
                        .build())
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .satisfies(extPrebid -> {
                    assertThat(extPrebid.getIntegration()).isEqualTo("integration");
                    assertThat(extPrebid.getTargeting()).isSameAs(bidRequest.getExt().getPrebid().getTargeting());
                });
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldReturnBidRequestWithAccountValues() {
        // given
        final String accountId = "accId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(emptyList())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.empty(),
                "ip");

        final Account account = Account.builder()
                .id(accountId)
                .auction(AccountAuctionConfig.builder()
                        .defaultIntegration("accountIntegration")
                        .targeting(AccountTargetingConfig.builder()
                                .includeWinners(false)
                                .includeBidderKeys(false)
                                .includeFormat(false)
                                .preferDeals(false)
                                .alwaysIncludeDeals(false)
                                .build())
                        .build())
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getExt)
                .extracting(ExtRequest::getPrebid)
                .satisfies(extPrebid -> {
                    assertThat(extPrebid.getIntegration()).isEqualTo("accountIntegration");
                    assertThat(extPrebid.getTargeting()).satisfies(targeting -> {
                        assertThat(targeting.getIncludewinners()).isFalse();
                        assertThat(targeting.getIncludebidderkeys()).isFalse();
                        assertThat(targeting.getIncludeformat()).isFalse();
                        assertThat(targeting.getPreferdeals()).isFalse();
                        assertThat(targeting.getAlwaysincludedeals()).isFalse();
                    });
                });
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldAddCountryFromPrivacy() {
        // given
        given(countryCodeMapper.mapToAlpha3("ua")).willReturn("UKR");

        final BidRequest bidRequest = givenBidRequest(identity());
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.builder()
                        .geoInfo(GeoInfo.builder().vendor("v").country("ua").build())
                        .build(),
                null);

        final Account account = Account.empty("id");

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getDevice)
                .extracting(Device::getGeo)
                .extracting(Geo::getCountry)
                .isEqualTo("UKR");
    }

    @Test
    public void enrichBidRequestWithGeolocationDataShouldAddCountryFromGeoInfo() {
        // given
        given(countryCodeMapper.mapToAlpha3("ua")).willReturn("UKR");

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(identity()))
                .geoInfo(GeoInfo.builder().vendor("v").country("ua").build())
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithGeolocationData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getDevice)
                .extracting(Device::getGeo)
                .extracting(Geo::getCountry)
                .isEqualTo("UKR");
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldAddRegionFromPrivacy() {
        // given
        given(countryCodeMapper.mapToAlpha3(any())).willReturn(null);

        final Device device = Device.builder()
                .geo(Geo.builder().region("regionInRequest").build())
                .build();
        final BidRequest bidRequest = givenBidRequest(requestCustomizer -> requestCustomizer.device(device));
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.builder()
                        .geoInfo(GeoInfo.builder().vendor("v").region("region").build())
                        .build(),
                null);

        final Account account = Account.empty("id");

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getDevice)
                .extracting(Device::getGeo)
                .extracting(Geo::getRegion)
                .isEqualTo("REGION");
    }

    @Test
    public void enrichBidRequestWithGeolocationDataShouldAddRegionFromPrivacy() {
        // given
        given(countryCodeMapper.mapToAlpha3(any())).willReturn(null);

        final Device device = Device.builder()
                .geo(Geo.builder().region("regionInRequest").build())
                .build();
        final BidRequest bidRequest = givenBidRequest(requestCustomizer -> requestCustomizer.device(device));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .geoInfo(GeoInfo.builder().vendor("v").region("region").build())
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithGeolocationData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getDevice)
                .extracting(Device::getGeo)
                .extracting(Geo::getRegion)
                .isEqualTo("REGION");
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldMakeRegionUpperCasedWhenNoPrivateGeoInfoProvided() {
        // given
        given(countryCodeMapper.mapToAlpha3(any())).willReturn(null);

        final Device device = Device.builder()
                .geo(Geo.builder().region("regionInRequest").build())
                .build();
        final BidRequest bidRequest = givenBidRequest(requestCustomizer -> requestCustomizer.device(device));

        final Account account = Account.empty("id");

        final PrivacyContext privacyContextWithoutRegion = PrivacyContext.of(
                null,
                TcfContext.builder().geoInfo(GeoInfo.builder().vendor("v").build()).build()
        );
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContextWithoutRegion)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getDevice)
                .extracting(Device::getGeo)
                .extracting(Geo::getRegion)
                .isEqualTo("REGIONINREQUEST");
    }

    @Test
    public void enrichBidRequestWithGeolocationDataShouldMakeRegionUpperCasedWhenNoGeoInfoProvided() {
        // given
        given(countryCodeMapper.mapToAlpha3(any())).willReturn(null);

        final Device device = Device.builder()
                .geo(Geo.builder().region("regionInRequest").build())
                .build();
        final BidRequest bidRequest = givenBidRequest(requestCustomizer -> requestCustomizer.device(device));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .geoInfo(GeoInfo.builder().vendor("v").build())
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithGeolocationData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getDevice)
                .extracting(Device::getGeo)
                .extracting(Geo::getRegion)
                .isEqualTo("REGIONINREQUEST");
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldAddIpAddressV4FromPrivacy() {
        // given
        given(ipAddressHelper.toIpAddress(anyString())).willReturn(IpAddress.of("ignored", IpAddress.IP.v4));

        final BidRequest bidRequest = givenBidRequest(identity());
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.builder().build(),
                "ipv4");

        final Account account = Account.empty("id");

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(
                        AuctionContext.builder()
                                .bidRequest(bidRequest)
                                .account(account)
                                .privacyContext(privacyContext)
                                .build());

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getDevice)
                .extracting(Device::getIp, Device::getIpv6)
                .containsOnly("ipv4", null);
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldAddIpAddressV6FromPrivacy() {
        // given
        given(ipAddressHelper.toIpAddress(anyString())).willReturn(IpAddress.of("ignored", IpAddress.IP.v6));

        final BidRequest bidRequest = givenBidRequest(identity());
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.builder().build(),
                "ipv6");

        final Account account = Account.empty("id");

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(
                        AuctionContext.builder()
                                .bidRequest(bidRequest)
                                .account(account)
                                .privacyContext(privacyContext)
                                .build());

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getDevice)
                .extracting(Device::getIp, Device::getIpv6)
                .containsOnly(null, "ipv6");
    }

    @Test
    public void executeEntrypointHooksShouldReturnExpectedHttpRequest() {
        // given
        final RoutingContext routingContext = mock(RoutingContext.class);
        final HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);

        given(routingContext.request()).willReturn(httpServerRequest);
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap().add("test", "test"));

        given(httpServerRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpServerRequest.absoluteURI()).willReturn("absoluteUri");
        given(httpServerRequest.scheme()).willReturn("https");
        given(httpServerRequest.remoteAddress()).willReturn(new SocketAddressImpl(1234, "host"));

        final CaseInsensitiveMultiMap updatedQueryParam = CaseInsensitiveMultiMap.builder()
                .add("urloverride", "overriddendomain.com")
                .build();
        final CaseInsensitiveMultiMap headerParams = CaseInsensitiveMultiMap.builder()
                .add("DHT", "1")
                .build();
        given(hookStageExecutor.executeEntrypointStage(any(), any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        EntrypointPayloadImpl.of(
                                updatedQueryParam,
                                headerParams,
                                bidRequestToString(BidRequest.builder()
                                        .app(App.builder().bundle("org.company.application").build())
                                        .build())))));

        final AuctionContext auctionContext =
                AuctionContext.builder().hookExecutionContext(hookExecutionContext).build();

        // when
        final Future<HttpRequestContext> result = target.executeEntrypointHooks(routingContext, "", auctionContext);

        // then
        final HttpRequestContext httpRequest = result.result();
        assertThat(httpRequest.getAbsoluteUri()).isEqualTo("absoluteUri");
        assertThat(httpRequest.getQueryParams()).isSameAs(updatedQueryParam);
        assertThat(httpRequest.getHeaders()).isSameAs(headerParams);
        assertThat(httpRequest.getBody()).isEqualTo("{\"app\":{\"bundle\":\"org.company.application\"}}");
        assertThat(httpRequest.getScheme()).isEqualTo("https");
        assertThat(httpRequest.getRemoteHost()).isEqualTo("host");
    }

    @Test
    public void shouldReturnFailedFutureIfEntrypointHooksRejectedRequest() {
        // given
        final RoutingContext routingContext = mock(RoutingContext.class);
        final HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);

        given(routingContext.request()).willReturn(httpServerRequest);
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpServerRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(hookStageExecutor.executeEntrypointStage(any(), any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(true, null)));

        final AuctionContext auctionContext =
                AuctionContext.builder().hookExecutionContext(hookExecutionContext).build();

        // when
        final Future<?> result = target.executeEntrypointHooks(routingContext, "", auctionContext);

        // then
        assertThat(result).isFailed();
        assertThat(result.cause()).isInstanceOf(Ortb2RequestFactory.RejectedRequestException.class);
        assertThat(((Ortb2RequestFactory.RejectedRequestException) result.cause()).getAuctionContext())
                .isEqualTo(auctionContext);
    }

    @Test
    public void shouldUseBidRequestModifiedByRawAuctionRequestHooks() {
        // given
        final BidRequest modifiedBidRequest = BidRequest.builder()
                .app(App.builder().bundle("org.company.application").build())
                .build();
        given(hookStageExecutor.executeRawAuctionRequestStage(any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false, AuctionRequestPayloadImpl.of(modifiedBidRequest))));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().site(Site.builder().build()).build())
                .hookExecutionContext(hookExecutionContext)
                .build();

        // when
        final Future<BidRequest> result = target.executeRawAuctionRequestHooks(auctionContext);

        // then
        assertThat(result.result()).isEqualTo(modifiedBidRequest);
    }

    @Test
    public void shouldReturnFailedFutureIfRawAuctionRequestHookRejectedRequest() {
        // given
        given(hookStageExecutor.executeRawAuctionRequestStage(any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(true, null)));

        final AuctionContext auctionContext = AuctionContext.builder()
                .hookExecutionContext(hookExecutionContext)
                .build();

        // when
        final Future<BidRequest> result = target.executeRawAuctionRequestHooks(auctionContext);

        // then
        assertThat(result).isFailed();
        assertThat(result.cause()).isInstanceOf(Ortb2RequestFactory.RejectedRequestException.class);
        assertThat(((Ortb2RequestFactory.RejectedRequestException) result.cause()).getAuctionContext())
                .isEqualTo(auctionContext);
    }

    @Test
    public void shouldUseBidRequestModifiedByProcessedAuctionRequestHooks() {
        // given
        final BidRequest modifiedBidRequest = BidRequest.builder()
                .app(App.builder().bundle("org.company.application").build())
                .build();
        given(hookStageExecutor.executeProcessedAuctionRequestStage(any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false, AuctionRequestPayloadImpl.of(modifiedBidRequest))));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().site(Site.builder().build()).build())
                .hookExecutionContext(hookExecutionContext)
                .build();

        // when
        final Future<BidRequest> result = target.executeProcessedAuctionRequestHooks(auctionContext);

        // then
        assertThat(result.result()).isEqualTo(modifiedBidRequest);
    }

    @Test
    public void shouldReturnFailedFutureIfProcessedAuctionRequestHookRejectedRequest() {
        // given
        given(hookStageExecutor.executeProcessedAuctionRequestStage(any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(true, null)));

        final AuctionContext auctionContext = AuctionContext.builder()
                .hookExecutionContext(hookExecutionContext)
                .build();

        // when
        final Future<BidRequest> result = target.executeProcessedAuctionRequestHooks(auctionContext);

        // then
        assertThat(result).isFailed();
        assertThat(result.cause()).isInstanceOf(Ortb2RequestFactory.RejectedRequestException.class);
        assertThat(((Ortb2RequestFactory.RejectedRequestException) result.cause()).getAuctionContext())
                .isEqualTo(auctionContext);
    }

    @Test
    public void restoreResultFromRejectionShouldReturnSuccessfulFutureWhenRequestRejected() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .requestRejected(false)
                .build();

        // when
        final Future<AuctionContext> result =
                target.restoreResultFromRejection(new Ortb2RequestFactory.RejectedRequestException(auctionContext));

        // then
        assertThat(result).succeededWith(AuctionContext.builder()
                .requestRejected(true)
                .build());
    }

    @Test
    public void restoreResultFromRejectionShouldReturnFailedFutureWhenNotRejectionException() {
        // given
        final InvalidRequestException exception = new InvalidRequestException("Request is not really valid");

        // when
        final Future<AuctionContext> result = target.restoreResultFromRejection(exception);

        // then
        assertThat(result).isFailed().isSameAs(exception);
    }

    @Test
    public void updateTimeoutShouldReturnSameContextIfNoNeedUpdates() {
        // given
        given(timeoutResolver.limitToMax(any())).willReturn(500L);
        given(timeoutFactory.create(eq(0L), eq(500L))).willReturn(timeout);
        given(timeout.getDeadline()).willReturn(500L);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(request -> request.tmax(500L)))
                .timeoutContext(TimeoutContext.of(0, timeout, 90))
                .build();

        // when
        final AuctionContext result = target.updateTimeout(auctionContext);

        // then
        assertThat(result).isSameAs(auctionContext);
    }

    @Test
    public void updateTimeoutShouldReturnContextWithUpdatedTimeout() {
        // given
        final Timeout updatedTimeout = mock(Timeout.class);

        given(timeoutResolver.limitToMax(any())).willReturn(500L);
        given(timeoutFactory.create(eq(0L), eq(500L))).willReturn(updatedTimeout);
        given(timeout.getDeadline()).willReturn(400L);
        given(updatedTimeout.getDeadline()).willReturn(500L);

        final TimeoutContext timeoutContext = TimeoutContext.of(0, timeout, 90);
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(request -> request.tmax(500L)))
                .timeoutContext(timeoutContext)
                .build();

        // when
        final AuctionContext result = target.updateTimeout(auctionContext);

        // then
        assertThat(result.getBidRequest()).isSameAs(auctionContext.getBidRequest());
        assertThat(result.getTimeoutContext()).isEqualTo(timeoutContext.with(updatedTimeout));
    }

    @Test
    public void updateTimeoutShouldReturnContextWithUpdatedBidRequestTmax() {
        // given
        given(timeoutResolver.limitToMax(any())).willReturn(500L);
        given(timeoutFactory.create(eq(0L), eq(500L))).willReturn(timeout);
        given(timeout.getDeadline()).willReturn(500L);

        final TimeoutContext timeoutContext = TimeoutContext.of(0, timeout, 90);
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(request -> request.tmax(600L)))
                .timeoutContext(timeoutContext)
                .build();

        // when
        final AuctionContext result = target.updateTimeout(auctionContext);

        // then
        assertThat(result.getBidRequest()).isEqualTo(givenBidRequest(request -> request.tmax(500L)));
        assertThat(result.getTimeoutContext()).isSameAs(timeoutContext);
    }

    @Test
    public void updateTimeoutShouldReturnContextWithUpdatedTimeoutAndBidRequestTmax() {
        // given
        final Timeout updatedTimeout = mock(Timeout.class);

        given(timeoutResolver.limitToMax(any())).willReturn(500L);
        given(timeoutFactory.create(eq(0L), eq(500L))).willReturn(updatedTimeout);
        given(timeout.getDeadline()).willReturn(400L);
        given(updatedTimeout.getDeadline()).willReturn(500L);

        final TimeoutContext timeoutContext = TimeoutContext.of(0, timeout, 90);
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(request -> request.tmax(600L)))
                .timeoutContext(timeoutContext)
                .build();

        // when
        final AuctionContext result = target.updateTimeout(auctionContext);

        // then
        assertThat(result.getBidRequest()).isEqualTo(givenBidRequest(request -> request.tmax(500L)));
        assertThat(result.getTimeoutContext()).isEqualTo(timeoutContext.with(updatedTimeout));
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldSetDsaFromAccountWhenRequestLacksDsa() {
        // given
        final String accountId = "accId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(emptyList())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.empty(),
                "");

        final Account account = Account.builder()
                .id(accountId)
                .privacy(AccountPrivacyConfig.builder()
                        .dsa(AccountDsaConfig.of(
                                DefaultDsa.of(0, 1, 2, List.of(DsaTransparency.of("", List.of(0)))), null))
                        .build())
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getRegs)
                .extracting(Regs::getExt)
                .extracting(ExtRegs::getDsa)
                .satisfies(dsa -> {
                    assertThat(dsa.getDsaRequired()).isEqualTo(0);
                    assertThat(dsa.getPubRender()).isEqualTo(1);
                    assertThat(dsa.getDataToPub()).isEqualTo(2);
                    assertThat(dsa.getTransparency()).satisfies(transparencies ->
                            assertThat(transparencies).isEqualTo(List.of(ExtRegsDsaTransparency.of("",
                                    List.of(0)))));
                });
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldNotSetDsaFromAccountWhenAccountLacksDefaultDsa() {
        // given
        final String accountId = "accId";
        final Regs regs = Regs.builder().build();
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(emptyList())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .regs(regs));

        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.empty(),
                "");

        final Account account = Account.builder()
                .id(accountId)
                .privacy(AccountPrivacyConfig.builder().dsa(AccountDsaConfig.of(null, null)).build())
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getRegs)
                .extracting(Regs::getExt)
                .isNull();
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getRegs)
                .isSameAs(regs);
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldNotSetDsaFromAccountWhenRequestContainsDsa() {
        // given
        final String accountId = "accId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(emptyList())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .regs(Regs.builder().ext(ExtRegs.of(null,
                                null,
                                null,
                                ExtRegsDsa.of(0,
                                        1,
                                        2,
                                        List.of(ExtRegsDsaTransparency.of("", List.of(0))))))
                        .build())
        );

        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.empty(),
                "");

        final Account account = Account.builder()
                .id(accountId)
                .privacy(AccountPrivacyConfig.builder()
                        .dsa(AccountDsaConfig.of(
                                DefaultDsa.of(3, 4, 5, List.of(DsaTransparency.of("domain", List.of(1)))), null))
                        .build())
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getRegs)
                .extracting(Regs::getExt)
                .extracting(ExtRegs::getDsa)
                .satisfies(dsa -> {
                    assertThat(dsa.getDsaRequired()).isEqualTo(0);
                    assertThat(dsa.getPubRender()).isEqualTo(1);
                    assertThat(dsa.getDataToPub()).isEqualTo(2);
                    assertThat(dsa.getTransparency()).satisfies(transparencies ->
                            assertThat(transparencies).isEqualTo(List.of(ExtRegsDsaTransparency.of("",
                                    List.of(0)))));
                });
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldSetDsaFromAccountWhenGdprScopeMatches() {
        // given
        final String accountId = "accId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(emptyList())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.builder().inGdprScope(true).build(),
                "");

        final Account account = Account.builder()
                .id(accountId)
                .privacy(AccountPrivacyConfig.builder()
                        .dsa(AccountDsaConfig.of(
                                DefaultDsa.of(0, 1, 2, List.of(DsaTransparency.of("", List.of(0)))), true))
                        .build())
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getRegs)
                .extracting(Regs::getExt)
                .extracting(ExtRegs::getDsa)
                .satisfies(dsa -> {
                    assertThat(dsa.getDsaRequired()).isEqualTo(0);
                    assertThat(dsa.getPubRender()).isEqualTo(1);
                    assertThat(dsa.getDataToPub()).isEqualTo(2);
                    assertThat(dsa.getTransparency()).satisfies(transparencies ->
                            assertThat(transparencies).isEqualTo(List.of(ExtRegsDsaTransparency.of("",
                                    List.of(0)))));
                });
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldNotSetDsaFromAccountWhenGdprScopeDoesntMatch() {
        // given
        final String accountId = "accId";
        final Regs regs = Regs.builder().build();
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(emptyList())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .regs(regs));

        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.builder().inGdprScope(false).build(),
                "");

        final Account account = Account.builder()
                .id(accountId)
                .privacy(AccountPrivacyConfig.builder()
                        .dsa(AccountDsaConfig.of(
                                DefaultDsa.of(0, 1, 2, List.of(DsaTransparency.of("", List.of(0)))), true))
                        .build())
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final Future<BidRequest> result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getRegs)
                .extracting(Regs::getExt)
                .isNull();
        assertThat(result).isSucceeded().unwrap()
                .extracting(BidRequest::getRegs)
                .isSameAs(regs);
    }

    private void givenTarget(int timeoutAdjustmentFactor) {
        target = new Ortb2RequestFactory(
                timeoutAdjustmentFactor,
                0.01,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                activityInfrastructureCreator,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper,
                hookStageExecutor,
                priceFloorProcessor,
                countryCodeMapper,
                metrics);
    }

    private static String bidRequestToString(BidRequest bidRequest) {
        try {
            return mapper.writeValueAsString(bidRequest);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()).build();
    }
}
