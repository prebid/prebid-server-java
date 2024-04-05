package org.prebid.server.auction.requestfactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.video.BidRequestVideo;
import com.iab.openrtb.request.video.PodError;
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
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.DebugResolver;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.PriceGranularity;
import org.prebid.server.auction.VideoStoredRequestProcessor;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.privacy.contextfactory.AuctionPrivacyContextFactory;
import org.prebid.server.auction.versionconverter.BidRequestOrtbVersionConversionManager;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.metric.MetricName;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

public class VideoRequestFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Ortb2RequestFactory ortb2RequestFactory;
    @Mock
    private VideoStoredRequestProcessor videoStoredRequestProcessor;
    @Mock
    private BidRequestOrtbVersionConversionManager ortbVersionConversionManager;
    @Mock
    private Ortb2ImplicitParametersResolver paramsResolver;
    @Mock
    private AuctionPrivacyContextFactory auctionPrivacyContextFactory;
    @Mock
    private GeoLocationServiceWrapper geoLocationServiceWrapper;

    private VideoRequestFactory target;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;
    @Mock
    private DebugResolver debugResolver;

    @Before
    public void setUp() {
        given(ortb2RequestFactory.createAuctionContext(any(), eq(MetricName.video)))
                .willReturn(AuctionContext.builder().build());
        given(ortb2RequestFactory.executeEntrypointHooks(any(), any(), any()))
                .willAnswer(invocation -> toHttpRequest(invocation.getArgument(0), invocation.getArgument(1)));
        given(ortb2RequestFactory.restoreResultFromRejection(any()))
                .willAnswer(invocation -> Future.failedFuture((Throwable) invocation.getArgument(0)));
        given(ortb2RequestFactory.enrichWithPriceFloors(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(ortb2RequestFactory.updateTimeout(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(ortb2RequestFactory.activityInfrastructureFrom(any()))
                .willReturn(Future.succeededFuture());

        given(ortbVersionConversionManager.convertToAuctionSupportedVersion(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(debugResolver.debugContextFrom(any()))
                .willReturn(DebugContext.of(true, true, null));

        given(routingContext.request()).willReturn(httpServerRequest);
        given(routingContext.queryParams()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpServerRequest.remoteAddress()).willReturn(new SocketAddressImpl(1234, "host"));
        given(httpServerRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(geoLocationServiceWrapper.lookup(any()))
                .willReturn(Future.succeededFuture(GeoInfo.builder().vendor("vendor").build()));

        final PrivacyContext defaultPrivacyContext = PrivacyContext.of(
                Privacy.builder()
                        .gdpr("0")
                        .consentString(EMPTY)
                        .ccpa(Ccpa.EMPTY)
                        .coppa(0)
                        .build(),
                TcfContext.empty());
        given(auctionPrivacyContextFactory.contextFrom(any()))
                .willReturn(Future.succeededFuture(defaultPrivacyContext));

        target = new VideoRequestFactory(
                Integer.MAX_VALUE,
                false,
                null,
                ortb2RequestFactory,
                videoStoredRequestProcessor,
                ortbVersionConversionManager,
                paramsResolver,
                auctionPrivacyContextFactory,
                debugResolver,
                jacksonMapper,
                geoLocationServiceWrapper);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBodyAsString()).willReturn(null);

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Incoming request has no body");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredRequestIsEnforcedAndIdIsNotProvided() throws JsonProcessingException {
        // given
        given(routingContext.getBodyAsString())
                .willReturn(mapper.writeValueAsString(BidRequestVideo.builder().build()));
        given(routingContext.request().headers()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.USER_AGENT_HEADER, "123"));
        target = new VideoRequestFactory(
                Integer.MAX_VALUE,
                true,
                null,
                ortb2RequestFactory,
                videoStoredRequestProcessor,
                ortbVersionConversionManager,
                paramsResolver,
                auctionPrivacyContextFactory,
                debugResolver,
                jacksonMapper,
                geoLocationServiceWrapper);

        // when
        final Future<?> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Unable to find required stored request id");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyExceedsMaxRequestSize() {
        // given
        target = new VideoRequestFactory(
                2,
                true,
                null,
                ortb2RequestFactory,
                videoStoredRequestProcessor,
                ortbVersionConversionManager,
                paramsResolver,
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
                .hasMessage("Request size exceeded max size of 2 bytes.");
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
                .element(0).asString().startsWith("Failed to decode:");
    }

    @Test
    public void shouldUseHeadersModifiedByEntrypointHooks() throws JsonProcessingException {
        // given
        final BidRequestVideo requestVideo = BidRequestVideo.builder().build();
        final String body = mapper.writeValueAsString(requestVideo);
        given(routingContext.getBodyAsString()).willReturn(body);

        given(routingContext.request().headers()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.USER_AGENT_HEADER, "user-agent-123"));

        doAnswer(invocation -> Future.succeededFuture(HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder()
                        .add(HttpUtil.USER_AGENT_HEADER, "user-agent-456")
                        .build())
                .queryParams(CaseInsensitiveMultiMap.empty())
                .body(body)
                .build()))
                .when(ortb2RequestFactory)
                .executeEntrypointHooks(any(), any(), any());

        final WithPodErrors<BidRequest> emptyMergeObject = WithPodErrors.of(null, null);
        given(videoStoredRequestProcessor.processVideoRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(emptyMergeObject));

        // when
        target.fromRequest(routingContext, 0L);

        // then
        verify(videoStoredRequestProcessor).processVideoRequest(any(), any(), any(), eq(BidRequestVideo.builder()
                .device(Device.builder()
                        .ua("user-agent-456")
                        .build())
                .build()));
    }

    @Test
    public void shouldEnrichAuctionContextWithDebugContext() throws JsonProcessingException {
        // given
        final BidRequestVideo requestVideo = BidRequestVideo.builder().device(
                Device.builder().ua("123").build()).build();
        given(routingContext.getBodyAsString()).willReturn(mapper.writeValueAsString(requestVideo));
        givenBidRequest(BidRequest.builder().build(), emptyList());

        // when
        final Future<WithPodErrors<AuctionContext>> result = target.fromRequest(routingContext, 0);

        // then
        verify(debugResolver).debugContextFrom(any());
        assertThat(result.result().getData().getDebugContext())
                .isEqualTo(DebugContext.of(true, true, null));
    }

    @Test
    public void shouldReturnExpectedResultAndReturnErrors() throws JsonProcessingException {
        // given
        final Content content = Content.builder()
                .len(900)
                .livestream(0)
                .build();
        final Imp expectedImp1 = Imp.builder()
                .id("123_0")
                .video(Video.builder()
                        .mimes(singletonList("mime"))
                        .maxduration(100)
                        .protocols(singletonList(123))
                        .build())
                .build();
        final Imp expectedImp2 = Imp.builder()
                .id("123_1")
                .video(Video.builder()
                        .mimes(singletonList("mime"))
                        .maxduration(100)
                        .protocols(singletonList(123))
                        .build())
                .build();
        final ExtRequestPrebid ext = ExtRequestPrebid.builder()
                .cache(ExtRequestPrebidCache.of(null, ExtRequestPrebidCacheVastxml.of(null, null), null))
                .targeting(ExtRequestTargeting.builder()
                        .pricegranularity(mapper.valueToTree(PriceGranularity.createFromString("med")))
                        .includebidderkeys(true)
                        .includebrandcategory(ExtIncludeBrandCategory.of(null, null, false, null))
                        .build())
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .id("bid_id")
                .imp(Arrays.asList(expectedImp1, expectedImp2))
                .user(User.builder().buyeruid("appnexus").yob(123).gender("gender").keywords("keywords").build())
                .site(Site.builder().id("siteId").content(content).build())
                .bcat(singletonList("bcat"))
                .badv(singletonList("badv"))
                .cur(singletonList("USD"))
                .tmax(0L)
                .ext(ExtRequest.of(ext))
                .build();

        final BidRequestVideo requestVideo = BidRequestVideo.builder().device(
                Device.builder().ua("123").build()).build();
        given(routingContext.getBodyAsString()).willReturn(mapper.writeValueAsString(requestVideo));

        final List<PodError> podErrors = singletonList(PodError.of(1, 1, singletonList("TEST")));
        givenBidRequest(bidRequest, podErrors);

        // when
        final Future<WithPodErrors<AuctionContext>> result = target.fromRequest(routingContext, 0L);

        // then
        verify(routingContext).getBodyAsString();
        verify(videoStoredRequestProcessor).processVideoRequest("", null, emptySet(), requestVideo);
        verify(ortb2RequestFactory).createAuctionContext(any(), eq(MetricName.video));
        verify(ortb2RequestFactory).enrichAuctionContext(any(), any(), eq(bidRequest), eq(0L));
        verify(ortb2RequestFactory).fetchAccountWithoutStoredRequestLookup(any());
        verify(ortb2RequestFactory).validateRequest(eq(bidRequest), any(), any());
        verify(paramsResolver)
                .resolve(eq(bidRequest), any(), eq(Endpoint.openrtb2_video.value()), eq(false));
        verify(ortb2RequestFactory).enrichBidRequestWithAccountAndPrivacyData(
                argThat(context -> Objects.equals(context.getBidRequest(), bidRequest)));
        assertThat(result.result().getData().getBidRequest()).isEqualTo(bidRequest);
        assertThat(result.result().getPodErrors()).isEqualTo(podErrors);
    }

    @Test
    public void shouldReplaceDeviceUaWithUserAgentHeaderIfPresented() throws JsonProcessingException {
        // given
        final BidRequestVideo requestVideo = BidRequestVideo.builder().build();
        given(routingContext.getBodyAsString()).willReturn(mapper.writeValueAsString(requestVideo));
        given(routingContext.request().headers()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.USER_AGENT_HEADER, "user-agent-123"));

        final WithPodErrors<BidRequest> emptyMergeObject = WithPodErrors.of(null, null);
        given(videoStoredRequestProcessor.processVideoRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(emptyMergeObject));

        // when
        target.fromRequest(routingContext, 0L);

        // then
        verify(videoStoredRequestProcessor).processVideoRequest(any(), any(), any(), eq(BidRequestVideo.builder()
                .device(Device.builder()
                        .ua("user-agent-123")
                        .build())
                .build()));
    }

    @Test
    public void shouldReturnErrorIfDeviceUaAndUserAgentHeaderIsEmpty() throws JsonProcessingException {
        // given
        final BidRequestVideo requestVideo = BidRequestVideo.builder().build();
        given(routingContext.getBodyAsString()).willReturn(mapper.writeValueAsString(requestVideo));
        given(httpServerRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        final Future<WithPodErrors<AuctionContext>> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Device.UA and User-Agent Header is not presented");
    }

    private void givenBidRequest(BidRequest bidRequest, List<PodError> podErrors) {
        given(videoStoredRequestProcessor.processVideoRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(WithPodErrors.of(bidRequest, podErrors)));
        given(ortb2RequestFactory.enrichAuctionContext(any(), any(), any(), anyLong()))
                .willAnswer(invocationOnMock -> AuctionContext.builder()
                        .httpRequest((HttpRequestContext) invocationOnMock.getArguments()[1])
                        .bidRequest((BidRequest) invocationOnMock.getArguments()[2])
                        .build());
        given(ortb2RequestFactory.fetchAccountWithoutStoredRequestLookup(any())).willReturn(Future.succeededFuture());

        given(ortb2RequestFactory.validateRequest(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture((BidRequest) invocation.getArgument(0)));

        given(paramsResolver.resolve(any(), any(), any(), anyBoolean()))
                .willAnswer(answerWithFirstArgument());

        given(ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(any()))
                .willAnswer(invocation -> Future.succeededFuture(((AuctionContext) invocation.getArgument(0))
                        .getBidRequest()));
        given(ortb2RequestFactory.enrichBidRequestWithGeolocationData(any()))
                .willAnswer(invocation -> Future.succeededFuture(((AuctionContext) invocation.getArgument(0))
                        .getBidRequest()));
        given(ortb2RequestFactory.executeProcessedAuctionRequestHooks(any()))
                .willAnswer(invocation -> Future.succeededFuture(
                        ((AuctionContext) invocation.getArgument(0)).getBidRequest()));
    }

    private Answer<Object> answerWithFirstArgument() {
        return invocationOnMock -> invocationOnMock.getArguments()[0];
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

    @Test
    public void fromRequestShouldCreateDebugCacheWhenQueryParamDebugIsPresent() throws JsonProcessingException {
        // given
        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap().add("debug", "true");
        given(routingContext.queryParams()).willReturn(queryParams);
        prepareMinimumSuccessfulConditions();

        // when
        final Future<WithPodErrors<AuctionContext>> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.result().getData().getCachedDebugLog().isEnabled()).isTrue();
    }

    @Test
    public void fromRequestShouldSetTestOneToBidRequestWhenCachedDebugLogIsEnabled() throws JsonProcessingException {
        // given
        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap().add("debug", "true");
        given(routingContext.queryParams()).willReturn(queryParams);
        prepareMinimumSuccessfulConditions();

        // when
        final Future<WithPodErrors<AuctionContext>> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.result().getData().getBidRequest().getTest())
                .isEqualTo(1);
    }

    @Test
    public void fromRequestShouldCreateDebugCacheAndIncludeRequestWithHeaders() throws JsonProcessingException {
        // given
        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap().add("debug", "true");
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap().add("header1", "value1");
        given(routingContext.queryParams()).willReturn(queryParams);
        given(httpServerRequest.headers()).willReturn(headers);
        prepareMinimumSuccessfulConditions();

        // when
        final Future<WithPodErrors<AuctionContext>> result = target.fromRequest(routingContext, 0L);

        // then
        assertThat(result.result().getData().getCachedDebugLog().buildCacheBody())
                .containsSequence("""
                        <Request>{"device":{"ua":"123"}}</Request>
                        <Response></Response>
                        <Headers>header1: value1
                        </Headers>""");
    }

    @Test
    public void shouldUpdateTimeout() throws JsonProcessingException {
        // given
        prepareMinimumSuccessfulConditions();

        given(ortb2RequestFactory.updateTimeout(any()))
                .willAnswer(invocation -> {
                    final AuctionContext auctionContext = invocation.getArgument(0);
                    return auctionContext.with(auctionContext.getBidRequest().toBuilder().tmax(10000L).build());
                });

        // when
        final Future<WithPodErrors<AuctionContext>> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future).isSucceeded();
        assertThat(future.result())
                .extracting(WithPodErrors::getData)
                .extracting(AuctionContext::getBidRequest)
                .extracting(BidRequest::getTmax)
                .isEqualTo(10000L);
    }

    private void prepareMinimumSuccessfulConditions() throws JsonProcessingException {
        final BidRequestVideo requestVideo = BidRequestVideo.builder().device(Device.builder()
                .ua("123").build()).build();
        given(routingContext.getBodyAsString()).willReturn(mapper.writeValueAsString(requestVideo));
        final ExtRequestPrebid ext = ExtRequestPrebid.builder()
                .targeting(ExtRequestTargeting.builder().build())
                .build();
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder().build()))
                .ext(ExtRequest.of(ext))
                .build();
        givenBidRequest(bidRequest, singletonList(PodError.of(1, 1, singletonList("TEST"))));
    }
}
