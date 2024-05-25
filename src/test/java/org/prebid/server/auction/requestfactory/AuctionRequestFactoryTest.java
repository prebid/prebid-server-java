package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.InterstitialProcessor;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.StoredRequestProcessor;
import org.prebid.server.auction.gpp.AuctionGppService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.AuctionStoredResult;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.privacy.contextfactory.AuctionPrivacyContextFactory;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.cookie.CookieDeprecationService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRegsDsa;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class AuctionRequestFactoryTest extends VertxTest {

    private static final String ACCOUNT_ID = "acc_id";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Ortb2RequestFactory ortb2RequestFactory;
    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    @Mock
    private AuctionGppService auctionGppService;
    @Mock
    private CookieDeprecationService cookieDeprecationService;
    @Mock
    private ImplicitParametersExtractor paramsExtractor;
    @Mock
    private Ortb2ImplicitParametersResolver paramsResolver;
    @Mock
    private InterstitialProcessor interstitialProcessor;
    @Mock
    private OrtbTypesResolver ortbTypesResolver;
    @Mock
    private AuctionPrivacyContextFactory auctionPrivacyContextFactory;
    @Mock
    private DebugResolver debugResolver;
    @Mock
    private GeoLocationServiceWrapper geoLocationServiceWrapper;

    private AuctionRequestFactory target;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;

    private Account defaultAccount;
    private BidRequest defaultBidRequest;
    private AuctionContext defaultActionContext;

    @Before
    public void setUp() {
        defaultBidRequest = BidRequest.builder().build();
        defaultAccount = Account.empty(ACCOUNT_ID);

        final PrivacyContext defaultPrivacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("0")
                        .consentString(EMPTY)
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.empty());
        defaultActionContext = AuctionContext.builder()
                .requestTypeMetric(MetricName.openrtb2web)
                .bidRequest(defaultBidRequest)
                .account(defaultAccount)
                .prebidErrors(new ArrayList<>())
                .privacyContext(defaultPrivacyContext)
                .debugContext(DebugContext.of(true, true, null))
                .build();

        given(ortbVersionConversionManager.convertToAuctionSupportedVersion(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(auctionGppService.contextFrom(any())).willReturn(Future.succeededFuture());
        given(auctionGppService.updateBidRequest(any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(1234, "host"));

        given(debugResolver.debugContextFrom(any())).willReturn(DebugContext.of(true, true, null));

        given(ortb2RequestFactory.createAuctionContext(any(), any())).willReturn(defaultActionContext);
        given(ortb2RequestFactory.executeEntrypointHooks(any(), any(), any()))
                .willAnswer(invocation -> toHttpRequest(invocation.getArgument(0), invocation.getArgument(1)));
        given(ortb2RequestFactory.executeRawAuctionRequestHooks(any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        ((AuctionContext) invocation.getArgument(0)).getBidRequest()));
        given(ortb2RequestFactory.validateRequest(any(), any(), any()))
                .willAnswer(invocationOnMock -> Future.succeededFuture((BidRequest) invocationOnMock.getArgument(0)));
        given(ortb2RequestFactory.enrichWithPriceFloors(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(ortb2RequestFactory.updateTimeout(any())).willAnswer(invocation -> invocation.getArgument(0));

        given(paramsResolver.resolve(any(), any(), any(), anyBoolean()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));

        given(interstitialProcessor.process(any()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));

        given(auctionPrivacyContextFactory.contextFrom(any()))
                .willReturn(Future.succeededFuture(defaultPrivacyContext));

        given(ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        ((AuctionContext) invocation.getArgument(0)).getBidRequest()));
        given(ortb2RequestFactory.enrichBidRequestWithGeolocationData(any()))
                .willAnswer(invocation -> Future.succeededFuture(((AuctionContext) invocation.getArgument(0))
                        .getBidRequest()));
        given(ortb2RequestFactory.executeProcessedAuctionRequestHooks(any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        ((AuctionContext) invocation.getArgument(0)).getBidRequest()));
        given(ortb2RequestFactory.restoreResultFromRejection(any()))
                .willAnswer(invocation -> Future.failedFuture((Throwable) invocation.getArgument(0)));
        given(ortb2RequestFactory.activityInfrastructureFrom(any()))
                .willReturn(Future.succeededFuture());
        given(cookieDeprecationService.updateBidRequestDevice(any(), any()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));
        given(geoLocationServiceWrapper.lookup(any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("vendor").build()));

        target = new AuctionRequestFactory(
                Integer.MAX_VALUE,
                ortb2RequestFactory,
                storedRequestProcessor,
                ortbVersionConversionManager,
                auctionGppService,
                cookieDeprecationService,
                paramsExtractor,
                paramsResolver,
                interstitialProcessor,
                ortbTypesResolver,
                auctionPrivacyContextFactory,
                debugResolver,
                jacksonMapper,
                geoLocationServiceWrapper);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Incoming request has no body");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyExceedsMaxRequestSize() {
        // given
        target = new AuctionRequestFactory(
                1,
                ortb2RequestFactory,
                storedRequestProcessor,
                ortbVersionConversionManager,
                auctionGppService,
                cookieDeprecationService,
                paramsExtractor,
                paramsResolver,
                interstitialProcessor,
                ortbTypesResolver,
                auctionPrivacyContextFactory,
                debugResolver,
                jacksonMapper,
                geoLocationServiceWrapper);

        given(routingContext.getBodyAsString()).willReturn("body");

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request size exceeded max size of 1 bytes.");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBodyAsString()).willReturn("body");

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages()).hasSize(1)
                .element(0).asString().startsWith("Error decoding bidRequest: Unrecognized token 'body'");
    }

    @Test
    public void shouldFillBidRequestWithValuesFromHttpRequest() {
        // given
        final ExtRegsDsa dsa = ExtRegsDsa.of(1, 2, 3, emptyList());
        final BidRequest receivedBidRequest = BidRequest.builder()
                .regs(Regs.builder()
                        .ext(ExtRegs.of(0, "us_privacy", null, dsa))
                        .build())
                .build();

        givenBidRequest(receivedBidRequest);
        given(paramsExtractor.gpcFrom(any())).willReturn("1");

        // when
        target.fromRequest(routingContext, 0L);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getRegs())
                .extracting(Regs::getExt)
                .extracting(ExtRegs::getGdpr, ExtRegs::getUsPrivacy, ExtRegs::getGpc, ExtRegs::getDsa)
                .containsExactly(0, "us_privacy", "1", dsa);
    }

    @Test
    public void shouldUseBodyAndHeadersModifiedByEntrypointHooks() {
        // given
        final BidRequest receivedBidRequest = BidRequest.builder()
                .device(Device.builder().dnt(1).build())
                .site(Site.builder().domain("example.com").build())
                .build();

        givenBidRequest(receivedBidRequest);

        final String rawModifiedBidRequest = bidRequestToString(BidRequest.builder()
                .app(App.builder().bundle("org.company.application").build())
                .build());
        doAnswer(invocation -> Future.succeededFuture(HttpRequestContext.builder().body(rawModifiedBidRequest).build()))
                .when(ortb2RequestFactory)
                .executeEntrypointHooks(any(), any(), any());

        // when
        target.fromRequest(routingContext, 0L);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getSite()).isNull();
        assertThat(capturedRequest.getApp()).isEqualTo(App.builder().bundle("org.company.application").build());
    }

    @Test
    public void shouldReturnFailedFutureIfEntrypointHookRejectedRequest() {
        // given
        givenValidBidRequest(defaultBidRequest);

        final Throwable exception = new RuntimeException();
        doAnswer(invocation -> Future.failedFuture(exception))
                .when(ortb2RequestFactory)
                .executeEntrypointHooks(any(), any(), any());

        final AuctionContext auctionContext = AuctionContext.builder().requestRejected(true).build();
        doReturn(Future.succeededFuture(auctionContext))
                .when(ortb2RequestFactory)
                .restoreResultFromRejection(eq(exception));

        // when
        final Future<AuctionContext> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future).succeededWith(auctionContext);
    }

    @Test
    public void shouldEnrichAuctionContextWithDebugContext() {
        // given
        givenValidBidRequest();

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        verify(debugResolver).debugContextFrom(any());
        assertThat(result.result().getDebugContext()).isEqualTo(
                DebugContext.of(true, true, null));
    }

    @Test
    public void shouldUseBidRequestModifiedByRawAuctionRequestHooks() {
        // given
        givenValidBidRequest(BidRequest.builder()
                .site(Site.builder().domain("example.com").build())
                .build());

        final BidRequest modifiedBidRequest = BidRequest.builder()
                .app(App.builder().bundle("org.company.application").build())
                .build();
        doAnswer(invocation -> Future.succeededFuture(modifiedBidRequest))
                .when(ortb2RequestFactory)
                .executeRawAuctionRequestHooks(any());

        // when
        target.fromRequest(routingContext, 0L);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(storedRequestProcessor).processAuctionRequest(any(), captor.capture());

        final BidRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.getSite()).isNull();
        assertThat(capturedRequest.getApp()).isEqualTo(App.builder().bundle("org.company.application").build());
    }

    @Test
    public void shouldReturnFailedFutureIfRawAuctionRequestHookRejectedRequest() {
        // given
        givenValidBidRequest(defaultBidRequest);

        final Throwable exception = new RuntimeException();
        doAnswer(invocation -> Future.failedFuture(exception))
                .when(ortb2RequestFactory)
                .executeRawAuctionRequestHooks(any());

        final AuctionContext auctionContext = AuctionContext.builder().requestRejected(true).build();
        doReturn(Future.succeededFuture(auctionContext))
                .when(ortb2RequestFactory)
                .restoreResultFromRejection(eq(exception));

        // when
        final Future<AuctionContext> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future).succeededWith(auctionContext);
    }

    @Test
    public void shouldUseBidRequestModifiedByProcessedAuctionRequestHooks() {
        // given
        givenValidBidRequest(BidRequest.builder()
                .site(Site.builder().domain("example.com").build())
                .build());

        final BidRequest modifiedBidRequest = BidRequest.builder()
                .app(App.builder().bundle("org.company.application").build())
                .build();
        doAnswer(invocation -> Future.succeededFuture(modifiedBidRequest))
                .when(ortb2RequestFactory)
                .executeProcessedAuctionRequestHooks(any());

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0L);

        // then
        final BidRequest resultBidRequest = result.result().getBidRequest();
        assertThat(resultBidRequest.getSite()).isNull();
        assertThat(resultBidRequest.getApp()).isEqualTo(App.builder().bundle("org.company.application").build());
    }

    @Test
    public void shouldReturnFailedFutureIfProcessedAuctionRequestHookRejectedRequest() {
        // given
        givenValidBidRequest(defaultBidRequest);

        final Throwable exception = new RuntimeException();
        doAnswer(invocation -> Future.failedFuture(exception))
                .when(ortb2RequestFactory)
                .executeProcessedAuctionRequestHooks(any());

        final AuctionContext auctionContext = AuctionContext.builder().requestRejected(true).build();
        doReturn(Future.succeededFuture(auctionContext))
                .when(ortb2RequestFactory)
                .restoreResultFromRejection(eq(exception));

        // when
        final Future<AuctionContext> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future).succeededWith(auctionContext);
    }

    @Test
    public void shouldReturnFailedFutureIfEidsPermissionsContainsWrongDataType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(emptyList(), null))
                        .build()))
                .build();

        final ObjectNode requestNode = mapper.convertValue(bidRequest, ObjectNode.class);
        final JsonNode eidPermissionNode = mapper.convertValue(
                ExtRequestPrebidDataEidPermissions.of("source", emptyList()), JsonNode.class);

        requestNode
                .putObject("ext")
                .putObject("prebid")
                .putObject("data")
                .set("eidpermissions", eidPermissionNode);

        given(routingContext.getBodyAsString()).willReturn(requestNode.toString());

        // when
        final Future<?> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) result.cause()).getMessages()).hasSize(1)
                .allSatisfy(message ->
                        assertThat(message).startsWith("Error decoding bidRequest: Cannot deserialize value"));
    }

    @Test
    public void shouldReturnFailedFutureIfEidsPermissionsBiddersContainsWrongDataType() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(emptyList(), null))
                        .build()))
                .build();

        final ObjectNode requestNode = mapper.convertValue(bidRequest, ObjectNode.class);

        final ObjectNode eidPermissionNode = mapper.convertValue(
                ExtRequestPrebidDataEidPermissions.of("source", emptyList()), ObjectNode.class);

        eidPermissionNode.put("bidders", "notArrayValue");

        final ArrayNode arrayNode = requestNode
                .putObject("ext")
                .putObject("prebid")
                .putObject("data")
                .putArray("eidpermissions");
        arrayNode.add(eidPermissionNode);

        given(routingContext.getBodyAsString()).willReturn(requestNode.toString());

        // when
        final Future<?> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) result.cause()).getMessages()).hasSize(1)
                .allSatisfy(message ->
                        assertThat(message).startsWith("Error decoding bidRequest: Cannot construct instance"));
    }

    @Test
    public void shouldTolerateMissingImpExtWhenProcessingAliases() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().ext(null).build()))
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("alias", "bidder"))
                        .build()))
                .build();
        givenBidRequest(bidRequest);
        givenAuctionContext(bidRequest, defaultAccount);
        givenProcessStoredRequest(bidRequest);

        // when
        final Future<?> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.succeeded()).isTrue();
    }

    @Test
    public void shouldCallOrtbFieldsResolver() {
        // given
        givenValidBidRequest();

        // when
        target.fromRequest(routingContext, 0L).result();

        // then
        verify(ortbTypesResolver).normalizeBidRequest(any(), any(), any());
    }

    @Test
    public void shouldReturnFailedFutureIfOrtb2RequestFactoryReturnedFailedFuture() {
        // given
        givenValidBidRequest(BidRequest.builder().build());
        given(ortb2RequestFactory.fetchAccount(any())).willReturn(Future.failedFuture("error"));

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("error");
    }

    @Test
    public void fromRequestShouldSetWebRequestTypeMetricWhenSiteIsPresent() {
        // given
        givenValidBidRequest(BidRequest.builder().site(Site.builder().build()).build());

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result().getRequestTypeMetric()).isEqualTo(MetricName.openrtb2web);
    }

    @Test
    public void fromRequestShouldSetWebRequestTypeMetricWhenNoSiteAppOrDoohPresent() {
        // given
        givenValidBidRequest(BidRequest.builder().build());

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result().getRequestTypeMetric()).isEqualTo(MetricName.openrtb2web);
    }

    @Test
    public void fromRequestShouldSetAppRequestTypeMetricWhenAppIsPresent() {
        // given
        givenValidBidRequest(BidRequest.builder().app(App.builder().build()).build());

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result().getRequestTypeMetric()).isEqualTo(MetricName.openrtb2app);
    }

    @Test
    public void fromRequestShouldSetDoohRequestTypeMetricWhenDoohIsPresent() {
        // given
        givenValidBidRequest(BidRequest.builder().dooh(Dooh.builder().build()).build());

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result).isSucceeded();
        assertThat(result.result().getRequestTypeMetric()).isEqualTo(MetricName.openrtb2dooh);
    }

    @Test
    public void storedRequestProcessorShouldUseAccountIdFetchedByOrtb2RequestFactory() {
        // given
        givenValidBidRequest();

        // when
        target.fromRequest(routingContext, 0L);

        // then
        verify(storedRequestProcessor).processAuctionRequest(eq(ACCOUNT_ID), any());
    }

    @Test
    public void shouldReturnFailedFutureIfProcessStoredRequestsFailed() {
        // given
        givenValidBidRequest();
        given(storedRequestProcessor.processAuctionRequest(any(), any()))
                .willReturn(Future.failedFuture("error"));

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).hasMessage("error");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestValidationFailed() {
        // given
        givenValidBidRequest();

        given(ortb2RequestFactory.validateRequest(any(), any(), any()))
                .willReturn(Future.failedFuture(new InvalidRequestException("errors")));

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages()).containsOnly("errors");
    }

    @Test
    public void shouldReturnAuctionContextWithExpectedParameters() {
        // given
        givenValidBidRequest();

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result).isEqualTo(defaultActionContext);
    }

    @Test
    public void shouldReturnModifiedBidRequestInAuctionContextWhenRequestWasPopulatedWithImplicitParams() {
        // given
        givenValidBidRequest();

        final BidRequest updatedBidRequest = defaultBidRequest.toBuilder().id("updated").build();
        given(paramsResolver.resolve(any(), any(), any(), anyBoolean())).willReturn(updatedBidRequest);

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getBidRequest()).isEqualTo(updatedBidRequest);
    }

    @Test
    public void shouldReturnPopulatedPrivacyContextAndGetWhenPrivacyEnforcementReturnContext() {
        // given
        givenValidBidRequest();

        final GeoInfo geoInfo = GeoInfo.builder().vendor("vendor").city("found").build();
        final PrivacyContext privacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("1")
                        .consentString("consent")
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.builder().geoInfo(geoInfo).build());
        given(auctionPrivacyContextFactory.contextFrom(any()))
                .willReturn(Future.succeededFuture(privacyContext));

        // when
        final AuctionContext result = target.fromRequest(routingContext, 0L).result();

        // then
        assertThat(result.getPrivacyContext()).isEqualTo(privacyContext);
        assertThat(result.getGeoInfo()).isEqualTo(geoInfo);
    }

    @Test
    public void shouldConvertBidRequestToInternalOpenRTBVersion() {
        // given
        givenValidBidRequest();

        given(ortbVersionConversionManager.convertToAuctionSupportedVersion(any())).willAnswer(
                invocation -> ((BidRequest) invocation.getArgument(0))
                        .toBuilder()
                        .source(Source.builder().tid("uniqTid").build())
                        .build());

        // when
        target.fromRequest(routingContext, 0L);

        // then
        verify(paramsResolver).resolve(
                argThat(bidRequest -> bidRequest.getSource().equals(Source.builder().tid("uniqTid").build())),
                any(),
                any(),
                anyBoolean());
    }

    @Test
    public void shouldUpdateTimeout() {
        // given
        givenValidBidRequest();

        given(ortb2RequestFactory.updateTimeout(any()))
                .willAnswer(invocation -> {
                    final AuctionContext auctionContext = invocation.getArgument(0);
                    return auctionContext.with(auctionContext.getBidRequest().toBuilder().tmax(10000L).build());
                });

        // when
        final Future<AuctionContext> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future).isSucceeded();
        assertThat(future.result())
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getTmax)
                .isEqualTo(10000L);
    }

    private void givenBidRequest(BidRequest bidRequest) {
        try {
            given(routingContext.getBodyAsString()).willReturn(mapper.writeValueAsString(bidRequest));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void givenAuctionContext(BidRequest bidRequest, Account account) {
        given(ortb2RequestFactory.enrichAuctionContext(any(), any(), any(), anyLong()))
                .willReturn(defaultActionContext.toBuilder()
                        .bidRequest(bidRequest)
                        .build());
        given(ortb2RequestFactory.fetchAccount(any())).willReturn(Future.succeededFuture(account));
    }

    private void givenProcessStoredRequest(BidRequest bidRequest) {
        given(storedRequestProcessor.processAuctionRequest(any(), any()))
                .willReturn(Future.succeededFuture(AuctionStoredResult.of(false, bidRequest)));
    }

    private void givenValidBidRequest() {
        givenValidBidRequest(defaultBidRequest);
    }

    private void givenValidBidRequest(BidRequest bidRequest) {
        givenBidRequest(bidRequest);
        givenAuctionContext(bidRequest, defaultAccount);
        givenProcessStoredRequest(bidRequest);
    }

    private static String bidRequestToString(BidRequest bidRequest) {
        try {
            return mapper.writeValueAsString(bidRequest);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Future<HttpRequestContext> toHttpRequest(RoutingContext routingContext, String body) {
        return Future.succeededFuture(HttpRequestContext.builder()
                .absoluteUri(routingContext.request().absoluteURI())
                .queryParams(toCaseInsensitiveMultiMap(routingContext.queryParams()))
                .headers(toCaseInsensitiveMultiMap(routingContext.request().headers()))
                .body(body)
                .scheme(routingContext.request().scheme())
                .remoteHost(routingContext.request().remoteAddress().host())
                .build());
    }

    private static CaseInsensitiveMultiMap toCaseInsensitiveMultiMap(MultiMap originalMap) {
        final CaseInsensitiveMultiMap.Builder mapBuilder = CaseInsensitiveMultiMap.builder();
        originalMap.entries().forEach(entry -> mapBuilder.add(entry.getKey(), entry.getValue()));

        return mapBuilder.build();
    }
}
