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
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.RejectedRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.execution.v1.entrypoint.EntrypointPayloadImpl;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestWrapper;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisher;
import org.prebid.server.proto.openrtb.ext.request.ExtPublisherPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.validation.RequestValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

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
    private ApplicationSettings applicationSettings;
    @Mock
    private HookStageExecutor hookStageExecutor;

    private Ortb2RequestFactory target;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private Timeout timeout;

    private BidRequest defaultBidRequest;
    private HttpRequestWrapper httpRequestWrapper;
    private HookExecutionContext hookExecutionContext;

    @Before
    public void setUp() {
        defaultBidRequest = BidRequest.builder().build();

        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        httpRequestWrapper = HttpRequestWrapper.builder()
                .headers(headers)
                .build();
        hookExecutionContext = HookExecutionContext.of(Endpoint.openrtb2_auction);

        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(headers);
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(1234, "host"));

        given(timeoutResolver.resolve(any())).willReturn(2000L);
        given(timeoutResolver.adjustTimeout(anyLong())).willReturn(1900L);

        given(hookStageExecutor.executeEntrypointStage(any(), any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        EntrypointPayloadImpl.of(
                                invocation.getArgument(0),
                                invocation.getArgument(1),
                                invocation.getArgument(2)))));

        given(hookStageExecutor.executeRawAuctionRequestStage(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        AuctionRequestPayloadImpl.of(invocation.getArgument(0)))));

        target = new Ortb2RequestFactory(
                false,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                hookStageExecutor);
    }

    @Test
    public void shouldReturnFailedFutureIfAccountIsEnforcedAndIdIsNotProvided() {
        // given
        target = new Ortb2RequestFactory(
                true,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                hookStageExecutor);

        // when
        final Future<?> future = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, defaultBidRequest, null,
                2000, hookExecutionContext, new ArrayList<>());

        // then
        verify(applicationSettings, never()).getAccountById(any(), any());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Unauthorized account id: ");
    }

    @Test
    public void shouldReturnFailedFutureIfAccountIsEnforcedAndFailedGetAccountById() {
        // given
        target = new Ortb2RequestFactory(
                true,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                applicationSettings,
                hookStageExecutor);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        final String accountId = "absentId";
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .build();

        // when
        final Future<?> future = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest, null,
                2000, hookExecutionContext, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Unauthorized account id: absentId");
    }

    @Test
    public void shouldReturnFailedFutureIfAccountIsInactive() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .build();

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(Account.builder()
                        .id(accountId)
                        .status(AccountStatus.inactive)
                        .build()));

        // when
        final Future<AuctionContext> future = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                null, 2000, hookExecutionContext, new ArrayList<>());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Account accountId is inactive");
    }

    @Test
    public void shouldReturnFailedFutureWhenAccountIdIsBlacklisted() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("bad_acc").build()).build())
                .build();

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                null, 2000, hookExecutionContext, new ArrayList<>());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(BlacklistedAccountException.class)
                .hasMessage("Prebid-server has blacklisted Account ID: bad_acc, please reach out to the prebid "
                        + "server host.");
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherExt() {
        // given
        final String parentAccount = "parentAccount";
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(ExtPublisher.of(ExtPublisherPrebid.of(parentAccount)))
                                .build())
                        .build())
                .build();

        final Account account = Account.builder().id(parentAccount).build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                null, 2000, hookExecutionContext, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(parentAccount), any());

        assertThat(result.result().getAccount()).isSameAs(account);
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherIdWhenExtIsNull() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).ext(null).build())
                        .build())
                .build();

        final Account account = Account.builder().id(accountId).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                null, 2000, hookExecutionContext, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result().getAccount()).isSameAs(account);
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherIdWhenExtPublisherPrebidIsNull() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId)
                                .ext(ExtPublisher.empty())
                                .build())
                        .build())
                .build();

        final Account account = Account.builder().id(accountId).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                null, 2000, hookExecutionContext, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result().getAccount()).isSameAs(account);
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherIdWhenExtParentIsEmpty() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(ExtPublisher.of(ExtPublisherPrebid.of("")))
                                .build())
                        .build())
                .build();

        final Account account = Account.builder().id("accountId").build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(account));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                null, 2000, hookExecutionContext, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq("accountId"), any());

        assertThat(result.result().getAccount()).isSameAs(account);
    }

    @Test
    public void shouldReturnAuctionContextWithEmptyAccountIfNotFound() {
        // given
        final String parentAccount = "parentAccount";
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id("accountId")
                                .ext(ExtPublisher.of(ExtPublisherPrebid.of(parentAccount)))
                                .build())
                        .build())
                .build();

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("not found")));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                null, 2000, hookExecutionContext, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(parentAccount), any());

        assertThat(result.result().getAccount()).isEqualTo(Account.empty(parentAccount));
    }

    @Test
    public void shouldReturnAuctionContextWithEmptyAccountIfExceptionOccurred() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .build();

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                null, 2000, hookExecutionContext, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result().getAccount()).isEqualTo(Account.empty(accountId));
    }

    @Test
    public void shouldReturnAuctionContextWithEmptyAccountIfItIsMissingInRequest() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                null, 2000, hookExecutionContext, new ArrayList<>());

        // then
        verifyZeroInteractions(applicationSettings);

        assertThat(result.result().getAccount()).isEqualTo(Account.empty(""));
    }

    @Test
    public void shouldReturnExpectedAuctionContext() {
        // given
        final String accountId = "accountId";
        final long tmax = 1000L;
        final BidRequest bidRequest = BidRequest.builder()
                .app(App.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .tmax(tmax)
                .build();

        final Account account = Account.builder()
                .id(accountId)
                .status(AccountStatus.active)
                .build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        final long resolvedTimeout = 200L;
        final long adjustedTimeout = 250L;
        given(timeoutResolver.resolve(anyLong())).willReturn(resolvedTimeout);
        given(timeoutResolver.adjustTimeout(anyLong())).willReturn(adjustedTimeout);
        given(timeoutFactory.create(anyLong(), anyLong())).willReturn(timeout);

        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper);
        given(uidsCookieService.parseFromRequest(any(HttpRequestWrapper.class))).willReturn(uidsCookie);

        final int startTime = 100;
        final MetricName metricName = MetricName.openrtb2app;
        final ArrayList<String> errors = new ArrayList<>();

        // when
        final Future<AuctionContext> future = target.fetchAccountAndCreateAuctionContext(httpRequestWrapper, bidRequest,
                metricName, startTime, hookExecutionContext, errors);

        // then
        verify(timeoutResolver).resolve(tmax);
        verify(timeoutResolver).adjustTimeout(resolvedTimeout);
        verify(timeoutFactory).create(startTime, adjustedTimeout);

        verify(applicationSettings).getAccountById(accountId, timeout);
        verify(uidsCookieService).parseFromRequest(httpRequestWrapper);

        final AuctionContext expectedAuctionContext = AuctionContext.builder()
                .httpRequest(httpRequestWrapper)
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .requestTypeMetric(metricName)
                .timeout(timeout)
                .account(account)
                .prebidErrors(errors)
                .debugWarnings(new ArrayList<>())
                .hookExecutionContext(hookExecutionContext)
                .build();
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(expectedAuctionContext);
    }

    @Test
    public void validateRequestShouldThrowInvalidRequestExceptionIfRequestIsInvalid() {
        // given
        given(requestValidator.validate(any())).willReturn(ValidationResult.error("error"));

        final BidRequest bidRequest = BidRequest.builder().build();

        // when and then
        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> target.validateRequest(bidRequest))
                .withMessage("error");

        verify(requestValidator).validate(bidRequest);
    }

    @Test
    public void validateRequestShouldReturnSameBidRequest() {
        // given
        given(requestValidator.validate(any())).willReturn(ValidationResult.success());

        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidRequest result = target.validateRequest(bidRequest);

        // then
        verify(requestValidator).validate(bidRequest);

        assertThat(result).isSameAs(bidRequest);
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldReturnIntegrationFromAccount() {
        // given
        final String accountId = "accId";
        final BidRequest bidRequest = BidRequest.builder()
                .imp(new ArrayList<>())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build()))
                .build();

        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.of("", "", Ccpa.EMPTY, 0),
                TcfContext.empty(),
                "ip");

        final String integration = "integration";
        final Account account = Account.builder().id(accountId).defaultIntegration(integration).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final BidRequest result = target.enrichBidRequestWithAccountAndPrivacyData(bidRequest, account, privacyContext);

        // then
        assertThat(result)
                .extracting(auctionBidRequest -> auctionBidRequest.getExt().getPrebid().getIntegration())
                .containsOnly(integration);
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldAddIpAddressAndCountryFromPrivacy() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.of("", "", Ccpa.EMPTY, 0),
                TcfContext.builder()
                        .geoInfo(GeoInfo.builder().vendor("v").country("ua").build())
                        .build(),
                "ip");

        final Account account = Account.empty("id");

        // when
        final BidRequest result = target.enrichBidRequestWithAccountAndPrivacyData(bidRequest, account, privacyContext);

        // then
        final Device expectedDevice = Device.builder()
                .ip("ip")
                .geo(Geo.builder().country("ua").build())
                .build();

        assertThat(result)
                .extracting(BidRequest::getDevice)
                .containsOnly(expectedDevice);
    }

    @Test
    public void executeEntrypointHooksShouldReturnExpectedHttpRequestWrapper() {
        // given
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap().add("test", "test"));

        final MultiMap updatedQueryParam = MultiMap.caseInsensitiveMultiMap()
                .add("urloverride", "overriddendomain.com");
        final MultiMap headerParams = MultiMap.caseInsensitiveMultiMap()
                .add("DHT", "1");

        given(httpRequest.absoluteURI()).willReturn("absoluteUri");
        given(httpRequest.scheme()).willReturn("https");
        given(hookStageExecutor.executeEntrypointStage(any(), any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        EntrypointPayloadImpl.of(
                                updatedQueryParam,
                                headerParams,
                                bidRequestToString(BidRequest.builder()
                                        .app(App.builder().bundle("org.company.application").build())
                                        .build())))));

        // when
        final Future<HttpRequestWrapper> result = target.executeEntrypointHooks(routingContext, "",
                hookExecutionContext);

        // then
        final HttpRequestWrapper httpRequest = result.result();
        assertThat(httpRequest.getAbsoluteUri()).isEqualTo("absoluteUri");
        assertThat(httpRequest.getQueryParams()).isEqualTo(updatedQueryParam);
        assertThat(httpRequest.getHeaders()).isEqualTo(headerParams);
        assertThat(httpRequest.getBody()).isEqualTo("{\"app\":{\"bundle\":\"org.company.application\"}}");
        assertThat(httpRequest.getScheme()).isEqualTo("https");
        assertThat(httpRequest.getRemoteHost()).isEqualTo("host");
    }

    @Test
    public void shouldReturnFailedFutureIfEntrypointHooksRejectRequest() {
        // given
        given(hookStageExecutor.executeEntrypointStage(any(), any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(true, null)));

        // when
        final Future<?> result = target.executeEntrypointHooks(routingContext, "", hookExecutionContext);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(RejectedRequestException.class);
        assertThat(((RejectedRequestException) result.cause()).getHookExecutionContext())
                .isEqualTo(HookExecutionContext.of(Endpoint.openrtb2_auction));
    }

    @Test
    public void shouldUseBidRequestModifiedByRawAuctionRequestHooks() {
        // given
        final BidRequest modifiedBidRequest = BidRequest.builder()
                .app(App.builder().bundle("org.company.application").build())
                .build();
        given(hookStageExecutor.executeRawAuctionRequestStage(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false, AuctionRequestPayloadImpl.of(modifiedBidRequest))));

        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().site(Site.builder().build()).build())
                .build();

        // when
        final Future<BidRequest> result = target.executeRawAuctionRequestHooks(auctionContext, hookExecutionContext);

        // then
        assertThat(result.result()).isEqualTo(modifiedBidRequest);
    }

    @Test
    public void shouldReturnFailedFutureIfRawAuctionRequestHookRejectRequest() {
        // given
        given(hookStageExecutor.executeRawAuctionRequestStage(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(true, null)));

        final AuctionContext auctionContext = AuctionContext.builder().build();

        // when
        final Future<BidRequest> result = target.executeRawAuctionRequestHooks(auctionContext, hookExecutionContext);

        // then
        Assertions.assertThat(result.failed()).isTrue();
        Assertions.assertThat(result.cause()).isInstanceOf(RejectedRequestException.class);
        Assertions.assertThat(((RejectedRequestException) result.cause()).getHookExecutionContext())
                .isEqualTo(HookExecutionContext.of(Endpoint.openrtb2_auction));
    }

    private static String bidRequestToString(BidRequest bidRequest) {
        try {
            return mapper.writeValueAsString(bidRequest);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
