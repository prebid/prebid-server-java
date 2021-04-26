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
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.PriceGranularity;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.TimeoutResolver;
import org.prebid.server.auction.VideoStoredRequestProcessor;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.metric.MetricName;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class VideoRequestFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Ortb2RequestFactory ortb2RequestFactory;
    @Mock
    private Ortb2ImplicitParametersResolver paramsResolver;
    @Mock
    private VideoStoredRequestProcessor videoStoredRequestProcessor;
    @Mock
    private PrivacyEnforcementService privacyEnforcementService;

    private VideoRequestFactory target;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;
    @Mock
    private TimeoutResolver timeoutResolver;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpServerRequest);
        given(httpServerRequest.getParam(anyString())).willReturn("test");

        final PrivacyContext defaultPrivacyContext = PrivacyContext.of(
                Privacy.of("0", EMPTY, Ccpa.EMPTY, 0),
                TcfContext.empty());
        given(privacyEnforcementService.contextFromBidRequest(any()))
                .willReturn(Future.succeededFuture(defaultPrivacyContext));

        target = new VideoRequestFactory(
                Integer.MAX_VALUE,
                false,
                ortb2RequestFactory,
                paramsResolver,
                videoStoredRequestProcessor,
                privacyEnforcementService,
                timeoutResolver,
                jacksonMapper);
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
    public void shouldReturnFailedFutureIfStoredRequestIsEnforcedAndIdIsNotProvided() throws JsonProcessingException {
        // given
        given(routingContext.getBodyAsString())
                .willReturn(mapper.writeValueAsString(BidRequestVideo.builder().build()));
        given(routingContext.request().getHeader(HttpUtil.USER_AGENT_HEADER)).willReturn("123");
        target = new VideoRequestFactory(
                Integer.MAX_VALUE,
                true,
                ortb2RequestFactory,
                paramsResolver,
                videoStoredRequestProcessor,
                privacyEnforcementService,
                timeoutResolver,
                jacksonMapper);

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
                ortb2RequestFactory,
                paramsResolver,
                videoStoredRequestProcessor,
                privacyEnforcementService,
                timeoutResolver,
                jacksonMapper);

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
                        .includebrandcategory(ExtIncludeBrandCategory.of(null, null, false))
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
        verify(ortb2RequestFactory).fetchAccountAndCreateAuctionContext(
                routingContext, bidRequest, MetricName.video, false, 0, new ArrayList<>());
        verify(ortb2RequestFactory).validateRequest(bidRequest);
        verify(paramsResolver).resolve(bidRequest, routingContext, timeoutResolver);
        verify(ortb2RequestFactory).enrichBidRequestWithAccountAndPrivacyData(eq(bidRequest), any(), any());

        assertThat(result.result().getData().getBidRequest()).isEqualTo(bidRequest);
        assertThat(result.result().getPodErrors()).isEqualTo(podErrors);
    }

    @Test
    public void shouldReplaceDeviceUaWithUserAgentHeaderIfPresented() throws JsonProcessingException {
        // given
        final BidRequestVideo requestVideo = BidRequestVideo.builder().build();
        given(routingContext.getBodyAsString()).willReturn(mapper.writeValueAsString(requestVideo));
        given(routingContext.request().getHeader(HttpUtil.USER_AGENT_HEADER)).willReturn("user-agent-123");

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

        // when
        Future<WithPodErrors<AuctionContext>> future = target.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Device.UA and User-Agent Header is not presented");
    }

    private void givenBidRequest(BidRequest bidRequest, List<PodError> podErrors) {
        given(videoStoredRequestProcessor.processVideoRequest(any(), any(), any(), any()))
                .willReturn(Future.succeededFuture(WithPodErrors.of(bidRequest, podErrors)));
        given(ortb2RequestFactory.fetchAccountAndCreateAuctionContext(any(), any(), any(), anyBoolean(), anyLong(),
                any()))
                .willAnswer(invocationOnMock -> Future.succeededFuture(
                        AuctionContext.builder()
                                .bidRequest((BidRequest) invocationOnMock.getArguments()[1])
                                .build()));

        given(ortb2RequestFactory.validateRequest(any())).willAnswer(answerWithFirstArgument());
        given(paramsResolver.resolve(any(), any(), any()))
                .willAnswer(answerWithFirstArgument());

        given(ortb2RequestFactory.enrichBidRequestWithAccountAndPrivacyData(any(), any(), any()))
                .willAnswer(answerWithFirstArgument());
    }

    private Answer<Object> answerWithFirstArgument() {
        return invocationOnMock -> invocationOnMock.getArguments()[0];
    }

}
