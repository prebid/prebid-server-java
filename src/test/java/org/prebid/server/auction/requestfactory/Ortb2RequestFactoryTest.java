package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Publisher;
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
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionStoredResult;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.deals.DealsPopulator;
import org.prebid.server.deals.model.DeepDebugLog;
import org.prebid.server.deals.model.TxnLog;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private DealsPopulator dealsPopulator;
    @Mock
    private PriceFloorProcessor priceFloorProcessor;
    @Mock
    private CountryCodeMapper countryCodeMapper;
    @Mock
    private Metrics metrics;

    private final Clock clock = Clock.systemDefaultZone();

    private Ortb2RequestFactory target;

    @Mock
    private Timeout timeout;

    private BidRequest defaultBidRequest;
    private HttpRequestContext httpRequest;
    private HookExecutionContext hookExecutionContext;

    @Before
    public void setUp() {
        defaultBidRequest = BidRequest.builder().build();

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

        given(dealsPopulator.populate(any()))
                .willAnswer(invocationOnMock -> Future.succeededFuture(invocationOnMock.getArgument(0)));

        target = new Ortb2RequestFactory(
                false,
                0.01,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper,
                hookStageExecutor,
                dealsPopulator,
                priceFloorProcessor,
                countryCodeMapper,
                metrics,
                clock);
    }

    @Test
    public void shouldIncrementRejectedByInvalidAccountMetricsIfUnknownUser() {
        // given
        target = new Ortb2RequestFactory(
                true,
                0.01,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper,
                hookStageExecutor,
                dealsPopulator,
                priceFloorProcessor,
                countryCodeMapper,
                metrics,
                clock);

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
                        .build());

        // then
        verify(metrics).updateAccountRequestRejectedByInvalidAccountMetrics(eq("absentId"));
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfAccountIsEnforcedAndIdIsNotProvided() {
        // given
        target = new Ortb2RequestFactory(
                true,
                0.01,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper,
                hookStageExecutor,
                dealsPopulator,
                priceFloorProcessor,
                countryCodeMapper,
                metrics,
                clock);

        given(storedRequestProcessor.processAuctionRequest(any(), any()))
                .willReturn(Future.succeededFuture(AuctionStoredResult.of(false, givenBidRequest(identity()))));

        // when
        final Future<?> future = target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(defaultBidRequest)
                        .build());

        // then
        verify(applicationSettings, never()).getAccountById(any(), any());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Unauthorized account id: ");
    }

    @Test
    public void fetchAccountShouldReturnFailedFutureIfAccountIsEnforcedAndFailedGetAccountById() {
        // given
        target = new Ortb2RequestFactory(
                true,
                0.01,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper,
                hookStageExecutor,
                dealsPopulator,
                priceFloorProcessor,
                countryCodeMapper,
                metrics,
                clock);

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
                AuctionContext.builder().bidRequest(bidRequest).build());

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
                AuctionContext.builder().bidRequest(bidRequest).build());

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

        final Account account = Account.builder().id(parentAccount).build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder().bidRequest(bidRequest).build());

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

        final Account account = Account.builder().id(accountId).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder().bidRequest(bidRequest).build());

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

        final Account account = Account.builder().id(accountId).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder().bidRequest(bidRequest).build());

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

        final Account account = Account.builder().id("accountId").build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder().bidRequest(bidRequest).build());

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

        final Account account = Account.builder().id(accountId).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder().bidRequest(bidRequest).build());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result()).isSameAs(account);
    }

    @Test
    public void fetchAccountShouldReturnEmptyAccountIfNotFound() {
        // given
        final String parentAccount = "parentAccount";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(ExtPublisher.of(ExtPublisherPrebid.of(parentAccount)))
                                .build())
                        .build()));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("not found")));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(bidRequest)
                        .build());

        // then
        verify(applicationSettings).getAccountById(eq(parentAccount), any());

        assertThat(result.result()).isEqualTo(Account.empty(parentAccount));
    }

    @Test
    public void fetchAccountShouldReturnEmptyAccountIfExceptionOccurred() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder().bidRequest(bidRequest).build());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result()).isEqualTo(Account.empty(accountId));
    }

    @Test
    public void fetchAccountShouldReturnEmptyAccountIfItIsMissingInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        given(storedRequestProcessor.processAuctionRequest(any(), any()))
                .willReturn(Future.succeededFuture(AuctionStoredResult.of(false, bidRequest)));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(bidRequest)
                        .build());

        // then
        verifyNoInteractions(applicationSettings);

        assertThat(result.result()).isEqualTo(Account.empty(""));
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
    public void shouldFetchAccountFromStoredAndReturnFailedFutureIfValidIsEnforcedAndStoredLookupIsFailed() {
        // given
        target = new Ortb2RequestFactory(
                true,
                0.01,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper,
                hookStageExecutor,
                dealsPopulator,
                priceFloorProcessor,
                countryCodeMapper,
                metrics,
                clock);

        final BidRequest receivedBidRequest = givenBidRequest(identity());
        given(storedRequestProcessor.processAuctionRequest(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<Account> result = target.fetchAccount(
                AuctionContext.builder()
                        .httpRequest(httpRequest)
                        .bidRequest(receivedBidRequest)
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
                .txnLog(TxnLog.create())
                .debugHttpCalls(emptyMap())
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
                        .txnLog(TxnLog.create())
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
                .startTime(100L)
                .timeout(timeout)
                .prebidErrors(new ArrayList<>())
                .debugWarnings(new ArrayList<>())
                .hookExecutionContext(hookExecutionContext)
                .txnLog(TxnLog.create())
                .deepDebugLog(DeepDebugLog.create(false, clock))
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
    public void enrichAuctionContextShouldReturnAuctionContextWithDeepDebugLogWhenDeepDebugIsOff() {
        // when
        final AuctionContext auctionContext = target.enrichAuctionContext(
                AuctionContext.builder().build(),
                httpRequest,
                BidRequest.builder().build(),
                100);

        // then
        assertThat(auctionContext.getDeepDebugLog()).isNotNull().returns(false, DeepDebugLog::isDeepDebugEnabled);
    }

    @Test
    public void enrichAuctionContextShouldReturnAuctionContextWithDeepDebugLogWhenDeepDebugIsOn() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder().trace(TraceLevel.verbose).build()))
                .build();

        // when
        final AuctionContext auctionContext = target.enrichAuctionContext(
                AuctionContext.builder().build(),
                httpRequest,
                bidRequest,
                100);

        // then
        assertThat(auctionContext.getDeepDebugLog()).isNotNull().returns(true, DeepDebugLog::isDeepDebugEnabled);
    }

    @Test
    public void validateRequestShouldThrowInvalidRequestExceptionIfRequestIsInvalid() {
        // given
        given(requestValidator.validate(any())).willReturn(ValidationResult.error("error"));

        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Future<BidRequest> result = target.validateRequest(bidRequest, new ArrayList<>());

        // then
        assertThat(result).isFailed();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("error");

        verify(requestValidator).validate(bidRequest);
    }

    @Test
    public void validateRequestShouldReturnSameBidRequest() {
        // given
        given(requestValidator.validate(any())).willReturn(ValidationResult.success());

        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final BidRequest result = target.validateRequest(bidRequest, new ArrayList<>()).result();

        // then
        verify(requestValidator).validate(bidRequest);

        assertThat(result).isSameAs(bidRequest);
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldReturnIntegrationFromAccount() {
        // given
        final String accountId = "accId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(new ArrayList<>())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build())));

        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("")
                        .consentString("")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.empty(),
                "ip");

        final String integration = "integration";
        final Account account = Account.builder()
                .id(accountId)
                .auction(AccountAuctionConfig.builder()
                        .defaultIntegration(integration)
                        .build())
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(bidRequest)
                .account(account)
                .privacyContext(privacyContext)
                .build();

        // when
        final BidRequest result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(result)
                .extracting(auctionBidRequest -> auctionBidRequest.getExt().getPrebid().getIntegration())
                .isEqualTo(integration);
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
        final BidRequest result = target.enrichBidRequestWithAccountAndPrivacyData(auctionContext);

        // then
        assertThat(List.of(result))
                .extracting(BidRequest::getDevice)
                .extracting(Device::getGeo)
                .extracting(Geo::getCountry)
                .containsExactly("UKR");
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
        final BidRequest result = target.enrichBidRequestWithAccountAndPrivacyData(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .account(account)
                        .privacyContext(privacyContext)
                        .build());

        // then
        assertThat(Collections.singleton(result))
                .extracting(BidRequest::getDevice)
                .extracting(Device::getIp, Device::getIpv6)
                .containsOnly(tuple("ipv4", null));
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
        final BidRequest result = target.enrichBidRequestWithAccountAndPrivacyData(
                AuctionContext.builder()
                        .bidRequest(bidRequest)
                        .account(account)
                        .privacyContext(privacyContext)
                        .build());

        // then
        assertThat(Collections.singleton(result))
                .extracting(BidRequest::getDevice)
                .extracting(Device::getIp, Device::getIpv6)
                .containsOnly(tuple(null, "ipv6"));
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
                .timeout(timeout)
                .build();

        // when
        final AuctionContext result = target.updateTimeout(auctionContext, 0L);

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

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(request -> request.tmax(500L)))
                .timeout(timeout)
                .build();

        // when
        final AuctionContext result = target.updateTimeout(auctionContext, 0L);

        // then
        assertThat(result.getBidRequest()).isSameAs(auctionContext.getBidRequest());
        assertThat(result.getTimeout()).isEqualTo(updatedTimeout);
    }

    @Test
    public void updateTimeoutShouldReturnContextWithUpdatedBidRequestTmax() {
        // given
        given(timeoutResolver.limitToMax(any())).willReturn(500L);
        given(timeoutFactory.create(eq(0L), eq(500L))).willReturn(timeout);
        given(timeout.getDeadline()).willReturn(500L);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(request -> request.tmax(600L)))
                .timeout(timeout)
                .build();

        // when
        final AuctionContext result = target.updateTimeout(auctionContext, 0L);

        // then
        assertThat(result.getBidRequest()).isEqualTo(givenBidRequest(request -> request.tmax(500L)));
        assertThat(result.getTimeout()).isSameAs(timeout);
    }

    @Test
    public void updateTimeoutShouldReturnContextWithUpdatedTimeoutAndBidRequestTmax() {
        // given
        final Timeout updatedTimeout = mock(Timeout.class);

        given(timeoutResolver.limitToMax(any())).willReturn(500L);
        given(timeoutFactory.create(eq(0L), eq(500L))).willReturn(updatedTimeout);
        given(timeout.getDeadline()).willReturn(400L);
        given(updatedTimeout.getDeadline()).willReturn(500L);

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(givenBidRequest(request -> request.tmax(600L)))
                .timeout(timeout)
                .build();

        // when
        final AuctionContext result = target.updateTimeout(auctionContext, 0L);

        // then
        assertThat(result.getBidRequest()).isEqualTo(givenBidRequest(request -> request.tmax(500L)));
        assertThat(result.getTimeout()).isEqualTo(updatedTimeout);
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
