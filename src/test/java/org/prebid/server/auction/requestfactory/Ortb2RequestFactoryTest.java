package org.prebid.server.auction.requestfactory;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerRequest;
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
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
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
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class Ortb2RequestFactoryTest extends VertxTest {

    private static final List<String> BLACKLISTED_ACCOUNTS = singletonList("321");

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

    private Ortb2RequestFactory target;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private Timeout timeout;

    private BidRequest defaultBidRequest;

    @Before
    public void setUp() {
        defaultBidRequest = BidRequest.builder().build();

        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.headers()).willReturn(new CaseInsensitiveHeaders());

        given(timeoutResolver.resolve(any())).willReturn(2000L);
        given(timeoutResolver.adjustTimeout(anyLong())).willReturn(1900L);

        target = new Ortb2RequestFactory(
                false,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper);
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
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper);

        // when
        final Future<?> result = target.fetchAccountAndCreateAuctionContext(routingContext, defaultBidRequest, null,
                false, 2000, new ArrayList<>());

        // then
        verify(applicationSettings, never()).getAccountById(any(), any());

        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
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
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper);

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Not found")));

        final String accountId = "absentId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .app(App.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        // when
        final Future<?> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest, null,
                false, 2000, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Unauthorized account id: absentId");
    }

    @Test
    public void shouldReturnFailedFutureIfAccountIsInactive() {
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
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                null, false, 2000, new ArrayList<>());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(UnauthorizedAccountException.class)
                .hasMessage("Account accountId is inactive");
    }

    @Test
    public void shouldReturnFailedFutureWhenAccountIdIsBlacklisted() {
        // given
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id("321").build())
                        .build()));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                null,
                false, 2000, new ArrayList<>());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(BlacklistedAccountException.class)
                .hasMessage("Prebid-server has blacklisted Account ID: 321, please reach out to the prebid "
                        + "server host.");
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherExt() {
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
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                null, false, 2000, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(parentAccount), any());

        assertThat(result.result().getAccount()).isSameAs(account);
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherIdWhenExtIsNull() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).ext(null).build())
                        .build()));

        final Account account = Account.builder().id(accountId).build();
        given(applicationSettings.getAccountById(any(), any())).willReturn(Future.succeededFuture(account));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                null, false, 2000, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result().getAccount()).isSameAs(account);
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherIdWhenExtPublisherPrebidIsNull() {
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
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                null, false, 2000, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result().getAccount()).isSameAs(account);
    }

    @Test
    public void shouldReturnAuctionContextWithAccountIdTakenFromPublisherIdWhenExtParentIsEmpty() {
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
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                null, false, 2000, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq("accountId"), any());

        assertThat(result.result().getAccount()).isSameAs(account);
    }

    @Test
    public void shouldReturnAuctionContextWithEmptyAccountIfNotFound() {
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
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                null, false, 2000, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(parentAccount), any());

        assertThat(result.result().getAccount()).isEqualTo(Account.empty(parentAccount));
    }

    @Test
    public void shouldReturnAuctionContextWithEmptyAccountIfExceptionOccurred() {
        // given
        final String accountId = "accountId";
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build()));

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                null, false, 2000, new ArrayList<>());

        // then
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result().getAccount()).isEqualTo(Account.empty(accountId));
    }

    @Test
    public void shouldReturnAuctionContextWithEmptyAccountIfItIsMissingInRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                null, false, 2000, new ArrayList<>());

        // then
        verifyZeroInteractions(applicationSettings);

        assertThat(result.result().getAccount()).isEqualTo(Account.empty(""));
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

        given(storedRequestProcessor.processStoredRequests(any(), any()))
                .willReturn(Future.succeededFuture(mergedBidRequest));

        final Account fetchedAccount = Account.builder().id(accountId).status(AccountStatus.active).build();
        given(applicationSettings.getAccountById(any(), any()))
                .willReturn(Future.succeededFuture(fetchedAccount));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext,
                receivedBidRequest, null, true, 2000, new ArrayList<>());

        // then
        verify(storedRequestProcessor).processStoredRequests("", receivedBidRequest);
        verify(applicationSettings).getAccountById(eq(accountId), any());

        assertThat(result.result().getAccount()).isEqualTo(fetchedAccount);
    }

    @Test
    public void shouldFetchAccountFromStoredAndReturnFailedFutureWhenAccountIdIsBlacklisted() {
        // given
        final BidRequest receivedBidRequest = givenBidRequest(identity());

        final BidRequest mergedBidRequest = givenBidRequest(builder -> builder
                .site(Site.builder()
                        .publisher(Publisher.builder().id("321").build()).build()));

        given(storedRequestProcessor.processStoredRequests(any(), any()))
                .willReturn(Future.succeededFuture(mergedBidRequest));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext,
                receivedBidRequest, null, true, 2000, new ArrayList<>());

        // then
        verify(storedRequestProcessor).processStoredRequests("", receivedBidRequest);
        verifyZeroInteractions(applicationSettings);

        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(BlacklistedAccountException.class)
                .hasMessage("Prebid-server has blacklisted Account ID: 321, please reach out to the prebid "
                        + "server host.");
    }

    @Test
    public void shouldFetchAccountFromStoredAndReturnFailedFutureIfValidIsEnforcedAndStoredLookupIsFailed() {
        // given
        target = new Ortb2RequestFactory(
                true,
                BLACKLISTED_ACCOUNTS,
                uidsCookieService,
                requestValidator,
                timeoutResolver,
                timeoutFactory,
                storedRequestProcessor,
                applicationSettings,
                ipAddressHelper);

        final BidRequest receivedBidRequest = givenBidRequest(identity());
        given(storedRequestProcessor.processStoredRequests(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext,
                receivedBidRequest, null, true, 2000, new ArrayList<>());

        // then
        verify(storedRequestProcessor).processStoredRequests("", receivedBidRequest);
        verifyZeroInteractions(applicationSettings);

        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessage("error");
    }

    @Test
    public void shouldFetchAccountFromStoredAndReturnEmptyAccountIfStoredLookupIsFailed() {
        // given
        final BidRequest receivedBidRequest = givenBidRequest(identity());
        given(storedRequestProcessor.processStoredRequests(any(), any()))
                .willReturn(Future.failedFuture(new RuntimeException("error")));

        // when
        final Future<AuctionContext> result = target.fetchAccountAndCreateAuctionContext(routingContext,
                receivedBidRequest, null, true, 2000, new ArrayList<>());

        // then
        verify(storedRequestProcessor).processStoredRequests("", receivedBidRequest);
        verifyZeroInteractions(applicationSettings);

        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessage("error");
    }

    @Test
    public void shouldReturnExpectedAuctionContext() {
        // given
        final String accountId = "123";
        final long tmax = 1000L;
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .app(App.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .tmax(tmax));

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
        given(uidsCookieService.parseFromRequest(any())).willReturn(uidsCookie);

        final int startTime = 100;
        final MetricName metricName = MetricName.openrtb2app;
        final ArrayList<String> errors = new ArrayList<>();

        // when
        final Future<AuctionContext> future = target.fetchAccountAndCreateAuctionContext(routingContext, bidRequest,
                metricName, false, startTime, errors);

        // then
        verify(timeoutResolver).resolve(tmax);
        verify(timeoutResolver).adjustTimeout(resolvedTimeout);
        verify(timeoutFactory).create(startTime, adjustedTimeout);

        verify(applicationSettings).getAccountById(accountId, timeout);
        verify(uidsCookieService).parseFromRequest(routingContext);

        final AuctionContext expectedAuctionContext = AuctionContext.builder()
                .routingContext(routingContext)
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .requestTypeMetric(metricName)
                .timeout(timeout)
                .account(account)
                .prebidErrors(errors)
                .debugWarnings(new ArrayList<>())
                .build();
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo(expectedAuctionContext);
    }

    @Test
    public void validateRequestShouldThrowInvalidRequestExceptionIfRequestIsInvalid() {
        // given
        given(requestValidator.validate(any())).willReturn(ValidationResult.error("error"));

        final BidRequest bidRequest = givenBidRequest(identity());

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

        final BidRequest bidRequest = givenBidRequest(identity());

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
        final BidRequest bidRequest = givenBidRequest(builder -> builder
                .imp(new ArrayList<>())
                .site(Site.builder()
                        .publisher(Publisher.builder().id(accountId).build())
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder().build())));

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
    public void enrichBidRequestWithAccountAndPrivacyDataShouldAddCountryFromPrivacy() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.of("", "", Ccpa.EMPTY, 0),
                TcfContext.builder()
                        .geoInfo(GeoInfo.builder().vendor("v").country("ua").build())
                        .build(),
                null);

        final Account account = Account.empty("id");

        // when
        final BidRequest result = target.enrichBidRequestWithAccountAndPrivacyData(bidRequest, account, privacyContext);

        // then
        assertThat(singleton(result))
                .extracting(BidRequest::getDevice)
                .extracting(Device::getGeo)
                .extracting(Geo::getCountry)
                .containsOnly("ua");
    }

    @Test
    public void enrichBidRequestWithAccountAndPrivacyDataShouldAddIpAddressV4FromPrivacy() {
        // given
        given(ipAddressHelper.toIpAddress(anyString())).willReturn(IpAddress.of("ignored", IpAddress.IP.v4));

        final BidRequest bidRequest = givenBidRequest(identity());
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.of("", "", Ccpa.EMPTY, 0),
                TcfContext.builder().build(),
                "ipv4");

        final Account account = Account.empty("id");

        // when
        final BidRequest result = target.enrichBidRequestWithAccountAndPrivacyData(bidRequest, account, privacyContext);

        // then
        assertThat(singleton(result))
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
                Privacy.of("", "", Ccpa.EMPTY, 0),
                TcfContext.builder().build(),
                "ipv6");

        final Account account = Account.empty("id");

        // when
        final BidRequest result = target.enrichBidRequestWithAccountAndPrivacyData(bidRequest, account, privacyContext);

        // then
        assertThat(singleton(result))
                .extracting(BidRequest::getDevice)
                .extracting(Device::getIp, Device::getIpv6)
                .containsOnly(tuple(null, "ipv6"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> requestCustomizer) {
        return requestCustomizer.apply(BidRequest.builder()).build();
    }
}
