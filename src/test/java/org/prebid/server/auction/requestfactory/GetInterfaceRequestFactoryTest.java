package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.FpdResolver;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.ImplicitParametersExtractor;
import org.prebid.server.auction.InterstitialProcessor;
import org.prebid.server.auction.IpAddressHelper;
import org.prebid.server.auction.OrtbTypesResolver;
import org.prebid.server.auction.externalortb.ProfilesProcessor;
import org.prebid.server.auction.externalortb.StoredRequestProcessor;
import org.prebid.server.auction.gpp.AuctionGppService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.IpAddress;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.privacy.contextfactory.AuctionPrivacyContextFactory;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.bidadjustments.BidAdjustmentsEnricher;
import org.prebid.server.cookie.CookieDeprecationService;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ConsentedProvidersSettings;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.settings.model.Account;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.function.UnaryOperator.identity;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class GetInterfaceRequestFactoryTest extends VertxTest {

    private static final String ACCOUNT_ID = "acc_id";

    @Mock(strictness = LENIENT)
    private Ortb2RequestFactory ortb2RequestFactory;
    @Mock(strictness = LENIENT)
    private StoredRequestProcessor storedRequestProcessor;
    @Mock(strictness = LENIENT)
    private ProfilesProcessor profilesProcessor;
    @Mock(strictness = LENIENT)
    private BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    @Mock(strictness = LENIENT)
    private AuctionGppService auctionGppService;
    @Mock(strictness = LENIENT)
    private CookieDeprecationService cookieDeprecationService;
    @Mock(strictness = LENIENT)
    private ImplicitParametersExtractor paramsExtractor;
    @Mock(strictness = LENIENT)
    private OrtbTypesResolver ortbTypesResolver;
    @Mock(strictness = LENIENT)
    private IpAddressHelper ipAddressHelper;
    @Mock(strictness = LENIENT)
    private Ortb2ImplicitParametersResolver paramsResolver;
    @Mock(strictness = LENIENT)
    private FpdResolver fpdResolver;
    @Mock(strictness = LENIENT)
    private InterstitialProcessor interstitialProcessor;
    @Mock(strictness = LENIENT)
    private AuctionPrivacyContextFactory auctionPrivacyContextFactory;
    @Mock(strictness = LENIENT)
    private DebugResolver debugResolver;
    @Mock(strictness = LENIENT)
    private GeoLocationServiceWrapper geoLocationServiceWrapper;
    @Mock(strictness = LENIENT)
    private BidAdjustmentsEnricher bidAdjustmentsEnricher;

    private GetInterfaceRequestFactory target;

    @Mock(strictness = LENIENT)
    private RoutingContext routingContext;
    @Mock(strictness = LENIENT)
    private HttpServerRequest httpRequest;
    @Mock(strictness = LENIENT)
    private RequestBody requestBody;

    private BidRequest defaultBidRequest;
    private AuctionContext defaultAuctionContext;

    @BeforeEach
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(routingContext.body()).willReturn(requestBody);
        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.remoteAddress()).willReturn(new SocketAddressImpl(1234, "host"));

        defaultBidRequest = BidRequest.builder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .storedrequest(ExtStoredRequest.of("defaultSrid"))
                        .build()))
                .build();

        defaultAuctionContext = AuctionContext.builder()
                .httpRequest(toHttpRequest(routingContext, null).result())
                .bidRequest(defaultBidRequest)
                .requestTypeMetric(MetricName.openrtb2web)
                .prebidErrors(new ArrayList<>())
                .privacyContext(PrivacyContext.of(
                        Privacy.builder()
                                .gdpr("0")
                                .consentString(EMPTY)
                                .ccpa(Ccpa.EMPTY)
                                .coppa(0)
                                .build(),
                        TcfContext.empty()))
                .debugContext(DebugContext.of(true, true, null))
                .build();

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(2)));

        given(profilesProcessor.process(any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(1)));

        given(ortbVersionConversionManager.convertToAuctionSupportedVersion(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(auctionGppService.contextFrom(any())).willReturn(Future.succeededFuture());
        given(auctionGppService.updateBidRequest(any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(cookieDeprecationService.updateBidRequestDevice(any(), any()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));

        given(paramsResolver.resolve(any(), any(), any(), anyBoolean()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));

        given(interstitialProcessor.process(any()))
                .will(invocationOnMock -> invocationOnMock.getArgument(0));

        given(auctionPrivacyContextFactory.contextFrom(any()))
                .willReturn(Future.succeededFuture(defaultAuctionContext.getPrivacyContext()));

        given(debugResolver.debugContextFrom(any())).willReturn(defaultAuctionContext.getDebugContext());

        given(geoLocationServiceWrapper.lookup(any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("vendor").build()));

        given(bidAdjustmentsEnricher.enrichBidRequest(any()))
                .willAnswer(invocation -> ((AuctionContext) invocation.getArgument(0)).getBidRequest());

        given(ortb2RequestFactory.createAuctionContext(any(), any())).willReturn(defaultAuctionContext);
        given(ortb2RequestFactory.executeEntrypointHooks(any(), any(), any()))
                .willAnswer(invocation -> toHttpRequest(invocation.getArgument(0), invocation.getArgument(1)));
        given(ortb2RequestFactory.enrichAuctionContext(any(), any(), any(), anyLong()))
                .willAnswer(invocation -> ((AuctionContext) invocation.getArgument(0)).toBuilder()
                        .bidRequest(invocation.getArgument(2))
                        .build());
        given(ortb2RequestFactory.restoreResultFromRejection(any()))
                .willAnswer(invocation -> Future.failedFuture((Throwable) invocation.getArgument(0)));

        given(ortb2RequestFactory.fetchAccount(any())).willReturn(Future.succeededFuture(Account.empty(ACCOUNT_ID)));
        given(ortb2RequestFactory.enrichBidRequestWithGeolocationData(any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        ((AuctionContext) invocation.getArgument(0)).getBidRequest()));
        given(ortb2RequestFactory.activityInfrastructureFrom(any()))
                .willReturn(Future.succeededFuture());
        given(ortb2RequestFactory.removeEmptyEids(any(), any()))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(ortb2RequestFactory.limitImpressions(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(1)));
        given(ortb2RequestFactory.validateRequest(any(), any(), any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(invocation.getArgument(1)));
        given(ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        ((AuctionContext) invocation.getArgument(0)).getBidRequest()));
        given(ortb2RequestFactory.executeProcessedAuctionRequestHooks(any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        ((AuctionContext) invocation.getArgument(0)).getBidRequest()));
        given(ortb2RequestFactory.updateTimeout(any())).willAnswer(invocation -> invocation.getArgument(0));

        target = new GetInterfaceRequestFactory(
                ortb2RequestFactory,
                storedRequestProcessor,
                profilesProcessor,
                ortbVersionConversionManager,
                auctionGppService,
                cookieDeprecationService,
                paramsExtractor,
                ortbTypesResolver,
                ipAddressHelper,
                paramsResolver,
                fpdResolver,
                interstitialProcessor,
                auctionPrivacyContextFactory,
                debugResolver,
                jacksonMapper,
                geoLocationServiceWrapper,
                bidAdjustmentsEnricher);
    }

    @Test
    public void fromRequestShouldProceedAllExpectedSteps() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap().add("srid", "stored_id"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final InOrder inOrder = inOrder(ortb2RequestFactory);
        inOrder.verify(ortb2RequestFactory)
                .createAuctionContext(eq(Endpoint.openrtb2_get_interface), eq(MetricName.openrtb2web));
        inOrder.verify(ortb2RequestFactory).executeEntrypointHooks(any(), any(), any());
        inOrder.verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), any(), anyLong());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void fromRequestShouldRestoreResultFromRejection() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap());

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.failed()).isTrue();

        final InOrder inOrder = inOrder(ortb2RequestFactory);
        inOrder.verify(ortb2RequestFactory)
                .createAuctionContext(eq(Endpoint.openrtb2_get_interface), eq(MetricName.openrtb2web));
        inOrder.verify(ortb2RequestFactory).executeEntrypointHooks(any(), any(), any());
        inOrder.verify(ortb2RequestFactory).restoreResultFromRejection(any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void fromRequestShouldReadAllInitialFields() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                // Device
                .add("dnt", "0")
                .add("lmt", "1")
                .add("ua", "ua")
                .add("dtype", "2")
                .add("ifa", "ifa")
                .add("ifat", "ifaType")

                // User
                .add("addtl_consent", "consentedProviders")

                .add("tmax", "3")
                .add("bcat", "bCat0,bCat1")
                .add("badv", "bAdv0,bAdv1")

                // Regs
                .add("coppa", "4")
                .add("gdpr", "5")
                .add("gpps", "6,7")
                .add("gpc", "gpc")

                // Ext
                .add("debug", "8")
                .add("srid", "storedRequestId")
                .add("rprof", "requestProfile0,requestProfile1")
                .add("sarid", "storedAuctionResponseId")
                .add("of", "outputFormat")
                .add("om", "outputModule"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest).isEqualTo(BidRequest.builder()
                .device(Device.builder()
                        .dnt(0)
                        .lmt(1)
                        .ua("ua")
                        .devicetype(2)
                        .ifa("ifa")
                        .ext(ExtDevice.of(null, "ifaType", null))
                        .build())
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .consentedProvidersSettings(ConsentedProvidersSettings.of("consentedProviders"))
                                .build())
                        .build())
                .tmax(3L)
                .bcat(List.of("bCat0", "bCat1"))
                .badv(List.of("bAdv0", "bAdv1"))
                .regs(Regs.builder()
                        .coppa(4)
                        .gdpr(5)
                        .gppSid(List.of(6, 7))
                        .ext(ExtRegs.of(null, null, "gpc", null))
                        .build())
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .debug(8)
                        .storedrequest(ExtStoredRequest.of("storedRequestId"))
                        .profiles(List.of("requestProfile0", "requestProfile1"))
                        .storedAuctionResponse(ExtStoredAuctionResponse.of("storedAuctionResponseId", null, null))
                        .outputFormat("outputFormat")
                        .outputModule("outputModule")
                        .build()))
                .build());
    }

    @Test
    public void fromRequestShouldReadStoredRequestIdFromTagId() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap().add("tag_id", "storedRequestId"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getExt())
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getStoredrequest)
                .extracting(ExtStoredRequest::getId)
                .isEqualTo("storedRequestId");
    }

    @Test
    public void fromRequestShouldFailIfStoredRequestMissed() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap());

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isFalse();
        assertThat(result.cause()).hasMessage("Request require the stored request id.");
    }

    @Test
    public void fromRequestShouldFailOnInvalidTmax() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("tmax", "12a3"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isFalse();
        assertThat(result.cause()).hasMessage("Invalid number: For input string: \"12a3\"");
    }

    @Test
    public void fromRequestShouldUseDefaultDebug() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap().add("srid", "storedRequestId"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getExt())
                .extracting(ExtRequest::getPrebid)
                .extracting(ExtRequestPrebid::getDebug)
                .isEqualTo(0);
    }

    @Test
    public void fromRequestShouldReadIpV4() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("ip", "ip"));
        given(ipAddressHelper.toIpAddress(eq("ip")))
                .willReturn(IpAddress.of("ip", IpAddress.IP.v4));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getDevice())
                .isEqualTo(Device.builder()
                        .ip("ip")
                        .ext(ExtDevice.of(null, null, null))
                        .build());
    }

    @Test
    public void fromRequestShouldReadIpV6() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("ip", "ip"));
        given(ipAddressHelper.toIpAddress(eq("ip")))
                .willReturn(IpAddress.of("ip", IpAddress.IP.v6));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getDevice())
                .isEqualTo(Device.builder()
                        .ipv6("ip")
                        .ext(ExtDevice.of(null, null, null))
                        .build());
    }

    @Test
    public void fromRequestShouldReadGdprAs1FromGdprApplies() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("gdpr_applies", "true"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getGdpr)
                .isEqualTo(1);
    }

    @Test
    public void fromRequestShouldReadGdprAs0FromGdprApplies() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("gdpr_applies", "false"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getGdpr)
                .isEqualTo(0);
    }

    @Test
    public void fromRequestShouldReadGppSidFromAlias() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("gpp_sid", "1,2"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getGppSid)
                .asInstanceOf(InstanceOfAssertFactories.list(Integer.class))
                .containsExactly(1, 2);
    }

    @Test
    public void fromRequestShouldReadGpcFromHeaders() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap().add("srid", "storedRequestId"));
        given(paramsExtractor.gpcFrom(any())).willReturn("gpc");

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getExt)
                .extracting(ExtRegs::getGpc)
                .isEqualTo("gpc");
    }

    @Test
    public void fromRequestShouldReadTcfConsent() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("tcfc", "tcfConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("tcfConsent");
    }

    @Test
    public void fromRequestShouldReadUspConsent() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("usp", "usPrivacy"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy)
                .isEqualTo("usPrivacy");
    }

    @Test
    public void fromRequestShouldReadGppConsent() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("gppc", "gpp"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getGpp)
                .isEqualTo("gpp");
    }

    @Test
    public void fromRequestShouldIgnoreNonPrimaryConsentFromConsentStringParam() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("tcfc", "tcfConsent")
                .add("usp", "usPrivacy")
                .add("gppc", "gpp")
                .add("consent_type", "4")
                .add("consent_string", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("tcfConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("usPrivacy", "gpp");
    }

    @Test
    public void fromRequestShouldIgnoreNonPrimaryConsentFromGdprConsentParam() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("tcfc", "tcfConsent")
                .add("usp", "usPrivacy")
                .add("gppc", "gpp")
                .add("consent_type", "4")
                .add("gdpr_consent", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("tcfConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("usPrivacy", "gpp");
    }

    @Test
    public void fromRequestShouldUseNonPrimaryConsentFromConsentStringParamForTcfV1() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("usp", "usPrivacy")
                .add("gppc", "gpp")
                .add("consent_type", "1")
                .add("consent_string", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("oldConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("usPrivacy", "gpp");
    }

    @Test
    public void fromRequestShouldUseNonPrimaryConsentFromGdprConsentParamForTcfV1() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("usp", "usPrivacy")
                .add("gppc", "gpp")
                .add("consent_type", "1")
                .add("gdpr_consent", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("oldConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("usPrivacy", "gpp");
    }

    @Test
    public void fromRequestShouldUseNonPrimaryConsentFromConsentStringParamForTcfV2() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("usp", "usPrivacy")
                .add("gppc", "gpp")
                .add("consent_type", "2")
                .add("consent_string", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("oldConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("usPrivacy", "gpp");
    }

    @Test
    public void fromRequestShouldUseNonPrimaryConsentFromGdprConsentParamForTcfV2() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("usp", "usPrivacy")
                .add("gppc", "gpp")
                .add("consent_type", "2")
                .add("gdpr_consent", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("oldConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("usPrivacy", "gpp");
    }

    @Test
    public void fromRequestShouldUseNonPrimaryConsentFromConsentStringParamForUsPrivacy() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("tcfc", "tcfConsent")
                .add("gppc", "gpp")
                .add("consent_type", "3")
                .add("consent_string", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("tcfConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("oldConsent", "gpp");
    }

    @Test
    public void fromRequestShouldUseNonPrimaryConsentFromGdprConsentParamForUsPrivacy() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("tcfc", "tcfConsent")
                .add("gppc", "gpp")
                .add("consent_type", "3")
                .add("gdpr_consent", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("tcfConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("oldConsent", "gpp");
    }

    @Test
    public void fromRequestShouldUseNonPrimaryConsentFromConsentStringParamForGpp() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("tcfc", "tcfConsent")
                .add("usp", "usPrivacy")
                .add("consent_type", "4")
                .add("consent_string", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("tcfConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("usPrivacy", "oldConsent");
    }

    @Test
    public void fromRequestShouldUseNonPrimaryConsentFromGdprConsentParamForGpp() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("tcfc", "tcfConsent")
                .add("usp", "usPrivacy")
                .add("consent_type", "4")
                .add("gdpr_consent", "oldConsent"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), captor.capture(), anyLong());

        final BidRequest initialBidRequest = captor.getValue();
        assertThat(initialBidRequest.getUser())
                .extracting(User::getConsent)
                .isEqualTo("tcfConsent");
        assertThat(initialBidRequest.getRegs())
                .extracting(Regs::getUsPrivacy, Regs::getGpp)
                .containsExactly("usPrivacy", "oldConsent");
    }

    @Test
    public void fromRequestShouldEmitErrorOnInvalidConsentType() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("consent_type", "123"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().getPrebidErrors())
                .containsExactly("Invalid consent_type param passed");
    }

    @Test
    public void fromRequestShouldEmitErrorOnInvalidTcfConsent() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("tcfc", "invalid"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().getPrebidErrors())
                .containsExactly("TCF consent string has invalid format.");
    }

    @Test
    public void fromRequestShouldEmitErrorOnInvalidUspConsent() {
        // given
        givenQueryParams(MultiMap.caseInsensitiveMultiMap()
                .add("srid", "storedRequestId")
                .add("usp", "invalid"));

        // when
        final Future<AuctionContext> result = target.fromRequest(routingContext, 0);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result().getPrebidErrors())
                .containsExactly("UsPrivacy string has invalid format.");
    }

    @Test
    public void enrichAuctionContextShouldProceedAllExpectedSteps() {
        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final InOrder inOrder = inOrder(
                ortb2RequestFactory,
                debugResolver,
                storedRequestProcessor,
                profilesProcessor,
                geoLocationServiceWrapper,
                auctionGppService,
                ortbVersionConversionManager,
                paramsResolver,
                cookieDeprecationService,
                interstitialProcessor,
                auctionPrivacyContextFactory,
                bidAdjustmentsEnricher);

        inOrder.verify(ortb2RequestFactory).fetchAccount(any());
        inOrder.verify(debugResolver).debugContextFrom(any());
        inOrder.verify(storedRequestProcessor).processAmpRequest(any(), any(), any());
        inOrder.verify(profilesProcessor).process(any(), any());
        inOrder.verify(geoLocationServiceWrapper).lookup(any());
        inOrder.verify(ortb2RequestFactory).enrichBidRequestWithGeolocationData(any());
        inOrder.verify(auctionGppService).contextFrom(any());
        inOrder.verify(ortb2RequestFactory).activityInfrastructureFrom(any());
        inOrder.verify(ortbVersionConversionManager).convertToAuctionSupportedVersion(any());
        inOrder.verify(auctionGppService).updateBidRequest(any(), any());
        inOrder.verify(paramsResolver).resolve(any(), any(), any(), eq(true));
        inOrder.verify(cookieDeprecationService).updateBidRequestDevice(any(), any());
        inOrder.verify(ortb2RequestFactory).removeEmptyEids(any(), any());
        inOrder.verify(ortb2RequestFactory).limitImpressions(any(), any(), any());
        inOrder.verify(ortb2RequestFactory).validateRequest(any(), any(), any(), any(), any());
        inOrder.verify(interstitialProcessor).process(any());
        inOrder.verify(auctionPrivacyContextFactory).contextFrom(any());
        inOrder.verify(ortb2RequestFactory).enrichBidRequestWithAccountAndPrivacyData(any());
        inOrder.verify(bidAdjustmentsEnricher).enrichBidRequest(any());
        inOrder.verify(ortb2RequestFactory).executeProcessedAuctionRequestHooks(any());
        inOrder.verify(ortb2RequestFactory).updateTimeout(any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void enrichAuctionContextRestoreResultFromRejection() {
        // given
        given(ortb2RequestFactory.fetchAccount(any())).willReturn(Future.failedFuture("Failed"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.failed()).isTrue();

        final InOrder inOrder = inOrder(ortb2RequestFactory);
        inOrder.verify(ortb2RequestFactory).fetchAccount(any());
        inOrder.verify(ortb2RequestFactory).restoreResultFromRejection(any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void enrichAuctionContextShouldAddTempPublisherUsingAccountFromPubId() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap().add("pubid", "accountId"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<AuctionContext> captor1 = ArgumentCaptor.forClass(AuctionContext.class);
        final ArgumentCaptor<AuctionContext> captor2 = ArgumentCaptor.forClass(AuctionContext.class);
        verify(ortb2RequestFactory).fetchAccount(captor1.capture());
        verify(debugResolver).debugContextFrom(captor2.capture());

        assertThat(captor1.getValue())
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getSite)
                .isEqualTo(Site.builder()
                        .publisher(Publisher.builder().id("accountId").build())
                        .build());
        assertThat(captor2.getValue())
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getSite)
                .isNull();
    }

    @Test
    public void enrichAuctionContextShouldAddTempPublisherUsingAccountFromAccount() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap().add("account", "accountId"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<AuctionContext> captor1 = ArgumentCaptor.forClass(AuctionContext.class);
        final ArgumentCaptor<AuctionContext> captor2 = ArgumentCaptor.forClass(AuctionContext.class);
        verify(ortb2RequestFactory).fetchAccount(captor1.capture());
        verify(debugResolver).debugContextFrom(captor2.capture());

        assertThat(captor1.getValue())
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getSite)
                .isEqualTo(Site.builder()
                        .publisher(Publisher.builder().id("accountId").build())
                        .build());
        assertThat(captor2.getValue())
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getSite)
                .isNull();
    }

    @Test
    public void enrichAuctionContextShouldLimitImpsTo1() {
        // given
        final AuctionContext auctionContext = givenAuctionContext(givenBidRequest(
                givenImp(identity()),
                givenImp(identity())));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result())
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getImp)
                .extracting(List::size)
                .isEqualTo(1);
        assertThat(result.result())
                .extracting(AuctionContext::getPrebidErrors)
                .asInstanceOf(InstanceOfAssertFactories.list(String.class))
                .containsExactly("Request includes 2 imp elements. Only the first one will remain.");
    }

    @Test
    public void enrichAuctionContextShouldReadAllExpectedFieldsAndOverrideExistingValuesForImpWithBanner() {
        // given
        final BidRequest storedBidRequest = givenBidRequest(givenImp(imp -> imp
                .banner(Banner.builder()
                        .format(List.of(Format.builder().w(101).h(102).build()))
                        .w(103)
                        .h(104)
                        .btype(List.of(105))
                        .mimes(List.of("storedMimes"))
                        .battr(List.of(106))
                        .pos(107)
                        .topframe(108)
                        .expdir(List.of(109))
                        .api(List.of(110))
                        .ext(storedExt())
                        .build())));

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap()
                        .add("mimes", "mimes1,mimes2")
                        .add("battr", "1,2")
                        .add("api", "3,4")
                        .add("w", "5")
                        .add("h", "6")
                        .add("pos", "7")
                        .add("btype", "26,27")
                        .add("topframe", "28")
                        .add("expdir", "29,30"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());

        final BidRequest expectedBidRequest = givenBidRequest(givenImp(imp -> imp
                .banner(Banner.builder()
                        .format(List.of(Format.builder().w(5).h(6).build()))
                        .w(5)
                        .h(6)
                        .btype(List.of(26, 27))
                        .mimes(List.of("mimes1", "mimes2"))
                        .battr(List.of(1, 2))
                        .pos(7)
                        .topframe(28)
                        .expdir(List.of(29, 30))
                        .api(List.of(3, 4))
                        .ext(storedExt())
                        .build())));
        assertThat(captor.getValue()).isEqualTo(expectedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldPreserveValuesFromStoredRequestForImpBanner() {
        // given
        final BidRequest storedBidRequest = givenBidRequest(givenImp(imp -> imp
                .banner(Banner.builder()
                        .format(List.of(Format.builder().w(101).h(102).build()))
                        .w(103)
                        .h(104)
                        .btype(List.of(105))
                        .mimes(List.of("storedMimes"))
                        .battr(List.of(106))
                        .pos(107)
                        .topframe(108)
                        .expdir(List.of(109))
                        .api(List.of(110))
                        .ext(storedExt())
                        .build())));

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(storedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldReadAllExpectedFieldsAndOverrideExistingValuesForImpWithVideo() {
        // given
        final BidRequest storedBidRequest = givenBidRequest(givenImp(imp -> imp
                .video(Video.builder()
                        .w(111)
                        .h(112)
                        .mimes(List.of("storedMimes"))
                        .minduration(113)
                        .maxduration(114)
                        .startdelay(115)
                        .maxseq(116)
                        .poddur(117)
                        .protocols(List.of(118))
                        .podid(119)
                        .podseq(120)
                        .rqddurs(List.of(121))
                        .placement(122)
                        .plcmt(123)
                        .linearity(124)
                        .skip(125)
                        .skipmin(126)
                        .skipafter(127)
                        .sequence(128)
                        .slotinpod(129)
                        .mincpmpersec(BigDecimal.ONE)
                        .battr(List.of(130))
                        .pos(131)
                        .maxextended(132)
                        .minbitrate(133)
                        .maxbitrate(134)
                        .boxingallowed(135)
                        .playbackmethod(List.of(136))
                        .playbackend(137)
                        .delivery(List.of(138))
                        .api(List.of(139))
                        .ext(storedExt())
                        .build())));

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap()
                        .add("mimes", "mimes1,mimes2")
                        .add("battr", "1,2")
                        .add("api", "3,4")
                        .add("w", "5")
                        .add("h", "6")
                        .add("pos", "7")
                        .add("mindur", "8")
                        .add("maxdur", "9")
                        .add("startdelay", "10")
                        .add("maxseq", "11")
                        .add("poddur", "12")
                        .add("proto", "13,14")
                        .add("podid", "15")
                        .add("podseq", "16")
                        .add("rqddurs", "17,18")
                        .add("seq", "19")
                        .add("slotinpod", "20")
                        .add("mincpms", "21")
                        .add("maxex", "22")
                        .add("minbr", "23")
                        .add("maxbr", "24")
                        .add("delivery", "25")
                        .add("placement", "31")
                        .add("plcmt", "32")
                        .add("linearity", "33")
                        .add("skip", "34")
                        .add("skipmin", "35")
                        .add("skipafter", "36")
                        .add("boxingallowed", "37")
                        .add("playbackmethod", "38,39")
                        .add("playbackend", "40"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());

        final BidRequest expectedBidRequest = givenBidRequest(givenImp(imp -> imp
                .video(Video.builder()
                        .w(5)
                        .h(6)
                        .mimes(List.of("mimes1", "mimes2"))
                        .minduration(8)
                        .maxduration(9)
                        .startdelay(10)
                        .maxseq(11)
                        .poddur(12)
                        .protocols(List.of(13, 14))
                        .podid(15)
                        .podseq(16)
                        .rqddurs(List.of(17, 18))
                        .placement(31)
                        .plcmt(32)
                        .linearity(33)
                        .skip(34)
                        .skipmin(35)
                        .skipafter(36)
                        .sequence(19)
                        .slotinpod(20)
                        .mincpmpersec(new BigDecimal("21"))
                        .battr(List.of(1, 2))
                        .pos(7)
                        .maxextended(22)
                        .minbitrate(23)
                        .maxbitrate(24)
                        .boxingallowed(37)
                        .playbackmethod(List.of(38, 39))
                        .playbackend(40)
                        .delivery(List.of(25))
                        .api(List.of(3, 4))
                        .ext(storedExt())
                        .build())));
        assertThat(captor.getValue()).isEqualTo(expectedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldPreserveValuesFromStoredRequestForImpVideo() {
        // given
        final BidRequest storedBidRequest = givenBidRequest(givenImp(imp -> imp
                .video(Video.builder()
                        .w(111)
                        .h(112)
                        .mimes(List.of("storedMimes"))
                        .minduration(113)
                        .maxduration(114)
                        .startdelay(115)
                        .maxseq(116)
                        .poddur(117)
                        .protocols(List.of(118))
                        .podid(119)
                        .podseq(120)
                        .rqddurs(List.of(121))
                        .placement(122)
                        .plcmt(123)
                        .linearity(124)
                        .skip(125)
                        .skipmin(126)
                        .skipafter(127)
                        .sequence(128)
                        .slotinpod(129)
                        .mincpmpersec(BigDecimal.ONE)
                        .battr(List.of(130))
                        .pos(131)
                        .maxextended(132)
                        .minbitrate(133)
                        .maxbitrate(134)
                        .boxingallowed(135)
                        .playbackmethod(List.of(136))
                        .playbackend(137)
                        .delivery(List.of(138))
                        .api(List.of(139))
                        .ext(storedExt())
                        .build())));

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(storedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldReadAllExpectedFieldsAndOverrideExistingValuesForImpWithAudio() {
        // given
        final BidRequest storedBidRequest = givenBidRequest(givenImp(imp -> imp
                .audio(Audio.builder()
                        .mimes(List.of("storedMimes"))
                        .minduration(140)
                        .maxduration(141)
                        .startdelay(142)
                        .maxseq(143)
                        .poddur(144)
                        .protocols(List.of(145))
                        .podid(146)
                        .podseq(147)
                        .rqddurs(List.of(148))
                        .sequence(149)
                        .slotinpod(150)
                        .mincpmpersec(BigDecimal.ONE)
                        .battr(List.of(151))
                        .maxextended(152)
                        .minbitrate(153)
                        .maxbitrate(154)
                        .delivery(List.of(155))
                        .api(List.of(156))
                        .feed(157)
                        .stitched(158)
                        .nvol(159)
                        .ext(storedExt())
                        .build())));

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap()
                        .add("mimes", "mimes1,mimes2")
                        .add("battr", "1,2")
                        .add("api", "3,4")
                        .add("mindur", "8")
                        .add("maxdur", "9")
                        .add("startdelay", "10")
                        .add("maxseq", "11")
                        .add("poddur", "12")
                        .add("proto", "13,14")
                        .add("podid", "15")
                        .add("podseq", "16")
                        .add("rqddurs", "17,18")
                        .add("seq", "19")
                        .add("slotinpod", "20")
                        .add("mincpms", "21")
                        .add("maxex", "22")
                        .add("minbr", "23")
                        .add("maxbr", "24")
                        .add("delivery", "25")
                        .add("feed", "41")
                        .add("stitched", "42")
                        .add("nvol", "43"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());

        final BidRequest expectedBidRequest = givenBidRequest(givenImp(imp -> imp
                .audio(Audio.builder()
                        .mimes(List.of("mimes1", "mimes2"))
                        .minduration(8)
                        .maxduration(9)
                        .startdelay(10)
                        .maxseq(11)
                        .poddur(12)
                        .protocols(List.of(13, 14))
                        .podid(15)
                        .podseq(16)
                        .rqddurs(List.of(17, 18))
                        .sequence(19)
                        .slotinpod(20)
                        .mincpmpersec(new BigDecimal("21"))
                        .battr(List.of(1, 2))
                        .maxextended(22)
                        .minbitrate(23)
                        .maxbitrate(24)
                        .delivery(List.of(25))
                        .api(List.of(3, 4))
                        .feed(41)
                        .stitched(42)
                        .nvol(43)
                        .ext(storedExt())
                        .build())));
        assertThat(captor.getValue()).isEqualTo(expectedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldPreserveValuesFromStoredRequestForImpAudio() {
        // given
        final BidRequest storedBidRequest = givenBidRequest(givenImp(imp -> imp
                .audio(Audio.builder()
                        .mimes(List.of("storedMimes"))
                        .minduration(140)
                        .maxduration(141)
                        .startdelay(142)
                        .maxseq(143)
                        .poddur(144)
                        .protocols(List.of(145))
                        .podid(146)
                        .podseq(147)
                        .rqddurs(List.of(148))
                        .sequence(149)
                        .slotinpod(150)
                        .mincpmpersec(BigDecimal.ONE)
                        .battr(List.of(151))
                        .maxextended(152)
                        .minbitrate(153)
                        .maxbitrate(154)
                        .delivery(List.of(155))
                        .api(List.of(156))
                        .feed(157)
                        .stitched(158)
                        .nvol(159)
                        .ext(storedExt())
                        .build())));

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(storedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldReadAllExpectedFieldsAndOverrideExistingValuesForImp() {
        // given
        final BidRequest storedBidRequest = givenBidRequest(givenImp(imp -> imp
                .tagid("storedTagId")
                .ext(storedExt())));

        given(fpdResolver.resolveImpExt(any(), any()))
                .willAnswer(invocation -> invocation.getArgument(1));
        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap()
                        .add("slot", "slot")
                        .add("targeting", "{\"field\":\"value\"}")
                        .add("iprof", "impProfile1,impProfile2"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        verify(paramsExtractor).refererFrom(any());
        verify(ortbTypesResolver).normalizeTargeting(any(), any(), any());

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());

        final BidRequest expectedBidRequest = givenBidRequest(givenImp(imp -> imp
                .tagid("slot")
                .ext(mapper.valueToTree(Map.of(
                        "field", "value",
                        "prebid", Map.of("profiles", List.of("impProfile1", "impProfile2")))))));
        assertThat(captor.getValue()).isEqualTo(expectedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldPreserveValuesFromStoredRequestForImp() {
        // given
        final BidRequest storedBidRequest = givenBidRequest(givenImp(imp -> imp
                .tagid("storedTagId")
                .ext(storedExt())));

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(storedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldReadAllExpectedFieldsAndOverrideExistingValuesForChannels() {
        // given
        final Publisher storedPublisher = Publisher.builder()
                .id(ACCOUNT_ID)
                .name("publisher")
                .build();

        final Content storedContent = Content.builder()
                .id("contentId")
                .title("storedTitle")
                .series("storedSeries")
                .genre("storedGenre")
                .url("storedUrl")
                .cattax(160)
                .cat(List.of("storedCat"))
                .contentrating("storedContentRating")
                .livestream(161)
                .language("storedLanguage")
                .build();

        final BidRequest storedBidRequest = givenBidRequest(request -> request
                .site(Site.builder()
                        .id("siteId")
                        .page("storedPage")
                        .publisher(storedPublisher)
                        .content(storedContent)
                        .build())
                .app(App.builder()
                        .id("appId")
                        .name("storedName")
                        .bundle("storedBundle")
                        .storeurl("storedStoreUrl")
                        .publisher(storedPublisher)
                        .content(storedContent)
                        .build())
                .dooh(Dooh.builder()
                        .id("doohId")
                        .publisher(storedPublisher)
                        .content(storedContent)
                        .build()));

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap()
                        // content
                        .add("ctitle", "contentTitle")
                        .add("cseries", "contentSeries")
                        .add("cgenre", "contentGenre")
                        .add("curl", "contentUrl")
                        .add("ccattax", "44")
                        .add("ccat", "contentCat1,contentCat2")
                        .add("crating", "contentRating")
                        .add("clivestream", "45")
                        .add("clang", "contentLanguage")

                        // site
                        .add("page", "page")

                        // app
                        .add("name", "name")
                        .add("bundle", "bundle")
                        .add("storeurl", "storeUrl"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());

        final Content expectedContent = Content.builder()
                .id("contentId")
                .title("contentTitle")
                .series("contentSeries")
                .genre("contentGenre")
                .url("contentUrl")
                .cattax(44)
                .cat(List.of("contentCat1", "contentCat2"))
                .contentrating("contentRating")
                .livestream(45)
                .language("contentLanguage")
                .build();

        final BidRequest expectedBidRequest = givenBidRequest(request -> request
                .site(Site.builder()
                        .id("siteId")
                        .page("page")
                        .publisher(storedPublisher)
                        .content(expectedContent)
                        .build())
                .app(App.builder()
                        .id("appId")
                        .name("name")
                        .bundle("bundle")
                        .storeurl("storeUrl")
                        .publisher(storedPublisher)
                        .content(expectedContent)
                        .build())
                .dooh(Dooh.builder()
                        .id("doohId")
                        .publisher(storedPublisher)
                        .content(expectedContent)
                        .build()));
        assertThat(captor.getValue()).isEqualTo(expectedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldPreserveValuesFromStoredRequestForChannels() {
        // given
        final Publisher storedPublisher = Publisher.builder()
                .id(ACCOUNT_ID)
                .name("publisher")
                .build();

        final Content storedContent = Content.builder()
                .id("contentId")
                .title("storedTitle")
                .series("storedSeries")
                .genre("storedGenre")
                .url("storedUrl")
                .cattax(160)
                .cat(List.of("storedCat"))
                .contentrating("storedContentRating")
                .livestream(161)
                .language("storedLanguage")
                .build();

        final BidRequest storedBidRequest = givenBidRequest(request -> request
                .site(Site.builder()
                        .id("siteId")
                        .page("storedPage")
                        .publisher(storedPublisher)
                        .content(storedContent)
                        .build())
                .app(App.builder()
                        .id("appId")
                        .name("storedName")
                        .bundle("storedBundle")
                        .storeurl("storedStoreUrl")
                        .publisher(storedPublisher)
                        .content(storedContent)
                        .build())
                .dooh(Dooh.builder()
                        .id("doohId")
                        .publisher(storedPublisher)
                        .content(storedContent)
                        .build()));

        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(storedBidRequest));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(storedBidRequest);
    }

    @Test
    public void enrichAuctionContextShouldReadFormatProperlyFromSizes() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any())).willReturn(
                Future.succeededFuture(givenBidRequest(givenImp(imp -> imp.banner(Banner.builder().build())))));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap()
                        .add("ow", "1")
                        .add("w", "10")
                        .add("h", "2")
                        .add("sizes", "3x4,5x6"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue())
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .containsExactly(
                        Format.builder().w(1).h(2).build(),
                        Format.builder().w(3).h(4).build(),
                        Format.builder().w(5).h(6).build());
    }

    @Test
    public void enrichAuctionContextShouldReadFormatProperlyFromMs() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any())).willReturn(Future.succeededFuture(
                givenBidRequest(givenImp(imp -> imp.banner(Banner.builder().build())))));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap()
                        .add("w", "1")
                        .add("oh", "2")
                        .add("h", "20")
                        .add("ms", "3x4,5x6"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue())
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .extracting(Imp::getBanner)
                .flatExtracting(Banner::getFormat)
                .containsExactly(
                        Format.builder().w(1).h(2).build(),
                        Format.builder().w(3).h(4).build(),
                        Format.builder().w(5).h(6).build());
    }

    @Test
    public void enrichAuctionContextShouldReadWFromOw() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any())).willReturn(Future.succeededFuture(
                givenBidRequest(givenImp(imp -> imp.banner(Banner.builder().build())))));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap()
                        .add("ow", "1")
                        .add("w", "10"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue())
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .extracting(Imp::getBanner)
                .extracting(Banner::getW)
                .containsExactly(1);
    }

    @Test
    public void enrichAuctionContextShouldReadHFromOh() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any())).willReturn(
                Future.succeededFuture(givenBidRequest(givenImp(imp -> imp.banner(Banner.builder().build())))));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap()
                        .add("oh", "1")
                        .add("h", "10"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue())
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .extracting(Imp::getBanner)
                .extracting(Banner::getH)
                .containsExactly(1);
    }

    @Test
    public void enrichAuctionContextShouldUseExistentPrebidNodeForImpProfiles() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidRequest(
                        givenImp(imp -> imp.ext(mapper.valueToTree(Map.of("prebid", Map.of("field", "value"))))))));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap().add("iprof", "impProfile1,impProfile2"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue())
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .extracting(Imp::getExt)
                .containsExactly(mapper.valueToTree(Map.of(
                        "prebid", Map.of(
                                "field", "value",
                                "profiles", List.of("impProfile1", "impProfile2")))));
    }

    @Test
    public void enrichAuctionContextShouldCreatePrebidNodeForImpProfiles() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidRequest(givenImp(identity()))));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap().add("iprof", "impProfile1,impProfile2"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue())
                .extracting(BidRequest::getImp)
                .asInstanceOf(InstanceOfAssertFactories.list(Imp.class))
                .extracting(Imp::getExt)
                .containsExactly(mapper.valueToTree(Map.of(
                        "prebid", Map.of("profiles", List.of("impProfile1", "impProfile2")))));
    }

    @Test
    public void enrichAuctionContextShouldOverridePublisherId() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidRequest(request -> request.site(
                        Site.builder().publisher(Publisher.builder().id("id").build()).build()))));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap().add("ctitle", "contentTitle"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue())
                .extracting(BidRequest::getSite)
                .extracting(Site::getPublisher)
                .extracting(Publisher::getId)
                .isEqualTo(ACCOUNT_ID);
    }

    @Test
    public void enrichAuctionContextShouldCreateContent() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidRequest(request -> request.site(Site.builder().build()))));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap().add("ctitle", "contentTitle"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue())
                .extracting(BidRequest::getSite)
                .extracting(Site::getContent)
                .isNotNull();
    }

    @Test
    public void enrichAuctionContextShouldUseRssFeed() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidRequest(request -> request.site(Site.builder().build()))));

        final AuctionContext auctionContext = givenAuctionContext(
                MultiMap.caseInsensitiveMultiMap().add("rss_feed", "rssFeed"));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(auctionContext);

        // then
        assertThat(result.succeeded()).isTrue();

        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(profilesProcessor).process(any(), captor.capture());
        assertThat(captor.getValue())
                .extracting(BidRequest::getSite)
                .extracting(Site::getContent)
                .extracting(Content::getSeries)
                .isEqualTo("rssFeed");
    }

    @Test
    public void enrichAuctionContextShouldDetermineWebChannel() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidRequest(request -> request.site(Site.builder().build()))));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result())
                .extracting(AuctionContext::getRequestTypeMetric)
                .isEqualTo(MetricName.openrtb2web);
    }

    @Test
    public void enrichAuctionContextShouldDetermineAppChannel() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidRequest(request -> request.app(App.builder().build()))));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result())
                .extracting(AuctionContext::getRequestTypeMetric)
                .isEqualTo(MetricName.openrtb2app);
    }

    @Test
    public void enrichAuctionContextShouldDetermineDoohChannel() {
        // given
        given(storedRequestProcessor.processAmpRequest(any(), any(), any()))
                .willReturn(Future.succeededFuture(givenBidRequest(request -> request.dooh(Dooh.builder().build()))));

        // when
        final Future<AuctionContext> result = target.enrichAuctionContext(defaultAuctionContext);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result())
                .extracting(AuctionContext::getRequestTypeMetric)
                .isEqualTo(MetricName.openrtb2dooh);
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

    private void givenQueryParams(MultiMap params) {
        given(routingContext.queryParams()).willReturn(params);
    }

    private AuctionContext givenAuctionContext(MultiMap params) {
        final HttpRequestContext initialHttpRequestContext = defaultAuctionContext.getHttpRequest();
        return defaultAuctionContext.toBuilder()
                .httpRequest(HttpRequestContext.builder()
                        .absoluteUri(initialHttpRequestContext.getAbsoluteUri())
                        .queryParams(toCaseInsensitiveMultiMap(params))
                        .headers(initialHttpRequestContext.getHeaders())
                        .body(initialHttpRequestContext.getBody())
                        .scheme(initialHttpRequestContext.getScheme())
                        .remoteHost(initialHttpRequestContext.getRemoteHost())
                        .build())
                .build();
    }

    private AuctionContext givenAuctionContext(BidRequest bidRequest) {
        return defaultAuctionContext.with(bidRequest);
    }

    private BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer) {
        return bidRequestCustomizer.apply(defaultBidRequest.toBuilder()).build();
    }

    private BidRequest givenBidRequest(Imp... imps) {
        return givenBidRequest(request -> request.imp(List.of(imps)));
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()).build();
    }

    private static ObjectNode storedExt() {
        return mapper.createObjectNode().put("storedField", "storedValue");
    }
}
