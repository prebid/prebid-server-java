package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.video.BidRequestVideo;
import com.iab.openrtb.request.video.Pod;
import com.iab.openrtb.request.video.Podconfig;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.ParsedStoredDataResult;
import org.prebid.server.validation.RequestValidator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class VideoRequestFactoryTest extends VertxTest {

    private static final List<String> BLACKLISTED_ACCOUNTS = singletonList("bad_acc");

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private StoredRequestProcessor storedRequestProcessor;
    @Mock
    private ImplicitParametersExtractor paramsExtractor;
    @Mock
    private UidsCookieService uidsCookieService;
    @Mock
    private BidderCatalog bidderCatalog;
    @Mock
    private RequestValidator requestValidator;
    @Mock
    private InterstitialProcessor interstitialProcessor;
    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private AuctionRequestFactory auctionRequestFactory;

    private VideoRequestFactory factory;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;
    @Mock
    private TimeoutResolver timeoutResolver;
    @Mock
    private TimeoutFactory timeoutFactory;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpServerRequest);
        given(httpServerRequest.getParam(anyString())).willReturn("test");

        given(interstitialProcessor.process(any())).will(invocationOnMock -> invocationOnMock.getArgument(0));

        given(timeoutResolver.resolve(any())).willReturn(2000L);
        given(timeoutResolver.adjustTimeout(anyLong())).willReturn(1900L);

        factory = new VideoRequestFactory(BLACKLISTED_ACCOUNTS, storedRequestProcessor, auctionRequestFactory, timeoutResolver,
                false, BidRequest.builder().build(), Integer.MAX_VALUE);
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyIsMissing() {
        // given
        given(routingContext.getBody()).willReturn(null);

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Incoming request has no body");
    }

    @Test
    public void shouldReturnFailedFutureIfStoredRequestIsEnforcedAndIdIsNotProvided() {
        // given
        given(httpServerRequest.getParam(anyString())).willReturn(null);
        factory = new VideoRequestFactory(BLACKLISTED_ACCOUNTS, storedRequestProcessor, auctionRequestFactory, timeoutResolver,
                true, BidRequest.builder().build(), Integer.MAX_VALUE);

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        verify(routingContext, never()).getBody();

        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Unable to find required stored request id");
    }

    @Test
    public void shouldFailWhenRequestStoredrequestidIsMissing() {
        // given
        factory = new VideoRequestFactory(BLACKLISTED_ACCOUNTS, storedRequestProcessor, auctionRequestFactory, timeoutResolver,
                true, BidRequest.builder().build(), Integer.MAX_VALUE);

        givenValidDataResult(builder -> builder.storedrequestid(null), UnaryOperator.identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("request missing required field: storedrequestid");
    }

    @Test
    public void shouldFailWhenRequestPodConfigIsMissing() {
        // given
        givenValidDataResult(builder -> builder.podconfig(null), UnaryOperator.identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("request missing required field: PodConfig");
    }

    @Test
    public void shouldFailWhenRequestPodConfigPodsIsEmpty() {
        // given
        givenValidDataResult(UnaryOperator.identity(), podconfigBuilder -> podconfigBuilder.pods(emptyList()));

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("request missing required field: PodConfig.Pods");
    }

    @Test
    public void shouldFailWhenRequestPodConfigDurationRangeSecContainsNegative() {
        // given

        givenValidDataResult(UnaryOperator.identity(), podconfigBuilder -> podconfigBuilder.durationRangeSec(singletonList(-1)));

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("duration array require only positive numbers");
    }

    @Test
    public void shouldFailWhenRequestPodConfigDurationRangeSecContainsZero() {
        // given
        givenValidDataResult(UnaryOperator.identity(), podconfigBuilder -> podconfigBuilder.durationRangeSec(singletonList(0)));

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("duration array require only positive numbers");
    }

    @Test
    public void shouldFailWhenSiteAndAppIsMissing() {
        // given
        givenValidDataResult(requestBuilder -> requestBuilder.site(null).app(null), UnaryOperator.identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("request missing required field: site or app");
    }

    @Test
    public void shouldFailWhenSiteAndAppIsPresent() {
        // given
        givenValidDataResult(requestBuilder -> requestBuilder.app(App.builder().build()), UnaryOperator.identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("request.site or request.app must be defined, but not both");
    }

    @Test
    public void shouldFailWhenSiteIdAndSitePageIsMissing() {
        // given
        factory = new VideoRequestFactory(BLACKLISTED_ACCOUNTS, storedRequestProcessor, auctionRequestFactory, timeoutResolver,
                true, BidRequest.builder().build(), Integer.MAX_VALUE);

        givenValidDataResult(requestBuilder -> requestBuilder.site(Site.builder().build()), UnaryOperator.identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("request.site missing required field: id or page");
    }

    @Test
    public void shouldFailWhenAppIdIsBlocked() {
        // given
        givenValidDataResult(requestBuilder -> requestBuilder.site(null).app(App.builder().id("bad_acc").build()),
                UnaryOperator.identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Prebid-server does not process requests from App ID: bad_acc");
    }

    @Test
    public void shouldFailWhenAppIdAndAppBundleIsMissing() {
        // given
        givenValidDataResult(requestBuilder -> requestBuilder.site(null).app(App.builder().build()),
                UnaryOperator.identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("request.app missing required field: id or bundle");
    }

    @Test
    public void shouldFailWhenVideoMimesIsMissing() {
        // given
        givenValidDataResult(requestBuilder -> requestBuilder.video(Video.builder().build()), UnaryOperator.identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("request missing required field: Video.Mimes");
    }

    @Test
    public void shouldFailWhenVideoProtocolIsMissing() {
        // given
        givenValidDataResult(requestBuilder -> requestBuilder.video(Video.builder()
                        .mimes(singletonList("asd"))
                        .protocols(null)
                        .build()),
                UnaryOperator.identity());

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("request missing required field: Video.Protocols");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyExceedsMaxRequestSize() {
        // given
        factory = new VideoRequestFactory(BLACKLISTED_ACCOUNTS, storedRequestProcessor, auctionRequestFactory, timeoutResolver,
                true, BidRequest.builder().build(), 2);

        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Request size exceeded max size of 2 bytes.");
    }

    @Test
    public void shouldReturnFailedFutureIfRequestBodyCouldNotBeParsed() {
        // given
        given(routingContext.getBody()).willReturn(Buffer.buffer("body"));

        // when
        final Future<?> future = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(InvalidRequestException.class);
        assertThat(((InvalidRequestException) future.cause()).getMessages()).hasSize(1)
                .element(0).asString().startsWith("Failed to decode:");
    }

    @Test
    public void shouldMergeWithDefaultBidRequest() {
        // given
        final User user = User.builder()
                .yob(123)
                .buyeruid("buyeruid")
                .gender("gender")
                .keywords("keywords")
                .build();

        givenValidDataResult(requestBuilder -> requestBuilder.user(user)
                        .bcat(singletonList("bcat"))
                        .badv(singletonList("badv")),
                UnaryOperator.identity());

        // when
        factory.fromRequest(routingContext, 0L);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);
        verify(auctionRequestFactory).fillImplicitParameters(captor.capture(), any(), any());

        final Imp expectedImp1 = Imp.builder()
                .id("1_0")
                .video(Video.builder().mimes(singletonList("mime")).maxduration(100).protocols(singletonList(123)).build())
                .build();
        final Imp expectedImp2 = Imp.builder()
                .id("1_1")
                .video(Video.builder().mimes(singletonList("mime")).maxduration(100).protocols(singletonList(123)).build())
                .build();
        final ExtRequestPrebid ext = ExtRequestPrebid.builder()
                .cache(ExtRequestPrebidCache.of(null, ExtRequestPrebidCacheVastxml.of(null, null), null))
                .targeting(ExtRequestTargeting.builder()
                        .pricegranularity(Json.mapper.valueToTree(PriceGranularity.createFromString("med")))
                        .includebidderkeys(true)
                        .includebrandcategory(ExtIncludeBrandCategory.of(null, null, false))
                        .build())
                .build();
        final BidRequest expectedMergedRequest = BidRequest.builder()
                .id("bid_id")
                .imp(Arrays.asList(expectedImp1, expectedImp2))
                .user(User.builder().buyeruid("appnexus").yob(123).gender("gender").keywords("keywords").build())
                .site(Site.builder().id("siteId").build())
                .tmax(5000L)
                .bcat(singletonList("bcat"))
                .badv(singletonList("badv"))
                .ext(Json.mapper.valueToTree(ExtBidRequest.of(ext)))
                .build();

        assertThat(captor.getValue()).isEqualTo(expectedMergedRequest);
    }

    @Test
    public void shouldFailWhenAllPodsAreInvalid() {
        // given
        final Pod podWithoutId = Pod.of(null, 2, "cnf");
        final Pod podWithoutDurationSec = Pod.of(123, null, "cnf");
        final Pod podWithoutConfigId = Pod.of(123, 2, null);
        final Pod podWithBadDurationSec = Pod.of(222, -4, "cnf");
        final Pod podWithDuplicatedId = Pod.of(222, 4, "cnf");
        givenValidDataResult(UnaryOperator.identity(), podconfigBuilder -> podconfigBuilder.pods(
                Arrays.asList(podWithoutId, podWithoutDurationSec, podWithBadDurationSec, podWithoutConfigId, podWithDuplicatedId)));

        // when
        final Future<AuctionContext> result = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("all pods are incorrect:  request missing required field: PodConfig.Pods.PodId," +
                        " Pod index: 0; request missing or incorrect required field: PodConfig.Pods.AdPodDurationSec," +
                        " Pod index: 1; request incorrect required field: PodConfig.Pods.AdPodDurationSec is negative," +
                        " Pod index: 2; request duplicated required field: PodConfig.Pods.PodId," +
                        " Pod id: 123, request missing or incorrect required field: PodConfig.Pods.ConfigId," +
                        " Pod index: 3; request duplicated required field: PodConfig.Pods.PodId, Pod id: 222");
    }


    @Test
    public void shouldFailWhenThereAreNoStoredImpsFound() {
        // given
        final Pod pod1 = Pod.of(123, 2, "cnf");
        final Pod pod2 = Pod.of(321, 3, "cnf");
        givenValidDataResult(UnaryOperator.identity(), podconfigBuilder -> podconfigBuilder.pods(Arrays.asList(pod1, pod2)));

        // when
        final Future<AuctionContext> result = factory.fromRequest(routingContext, 0L);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("all pods are incorrect:  unable to load Pod id: 123; unable to load Pod id: 321");
    }

    @Test
    public void shouldCreateImpsFromStoredRequestAndReceivedVideoRequestPods() {
        // given
        final Pod pod1 = Pod.of(123, 20, "cnf");
        final Pod pod2 = Pod.of(321, 30, "cnf");
        final Pod podNoStored = Pod.of(1, 3, "cnf");
        final Map<String, Imp> idToImp = new HashMap<>();
        idToImp.put("123", Imp.builder().tagid("tagid1").build());
        idToImp.put("321", Imp.builder().tagid("tagid2").clickbrowser(2).build());
        givenValidDataResult(UnaryOperator.identity(),
                podconfigBuilder -> podconfigBuilder.pods(Arrays.asList(pod1, pod2, podNoStored)),
                idToImp);

        // when
        factory.fromRequest(routingContext, 0L);

        // then
        final ArgumentCaptor<BidRequest> captor = ArgumentCaptor.forClass(BidRequest.class);

        verify(auctionRequestFactory).fillImplicitParameters(captor.capture(), any(), any());

        final Video video = Video.builder().mimes(singletonList("mime")).maxduration(100).protocols(singletonList(123)).build();
        final Imp expectedImp1 = Imp.builder()
                .id("123_0")
                .tagid("tagid1")
                .video(video)
                .build();
        final Imp expectedImp1_1 = expectedImp1.toBuilder().id("123_1").build();
        final Imp expectedImp2 = Imp.builder()
                .id("321_0")
                .tagid("tagid2")
                .clickbrowser(2)
                .video(video)
                .build();
        final Imp expectedImp2_1 = expectedImp2.toBuilder().id("321_1").build();

        assertThat(captor.getValue().getImp()).isEqualTo(Arrays.asList(expectedImp1, expectedImp1_1, expectedImp2, expectedImp2_1));
    }

    private void givenValidDataResult(UnaryOperator<BidRequestVideo.BidRequestVideoBuilder> requestCustomizer,
                                      UnaryOperator<Podconfig.PodconfigBuilder> podconfigCustomizer) {
        givenValidDataResult(requestCustomizer, podconfigCustomizer, singletonMap("1", Imp.builder().build()));
    }

    private void givenValidDataResult(UnaryOperator<BidRequestVideo.BidRequestVideoBuilder> requestCustomizer,
                                      UnaryOperator<Podconfig.PodconfigBuilder> podconfigCustomizer,
                                      Map<String, Imp> storedIdToImps) {
        final BidRequestVideo videoRequest = requestCustomizer.apply(BidRequestVideo.builder()
                .storedrequestid("storedrequestid")
                .podconfig(podconfigCustomizer.apply(Podconfig.builder()
                        .pods(singletonList(Pod.of(1, 100, "configId")))
                        .durationRangeSec(Arrays.asList(200, 100)))
                        .build())
                .site(Site.builder().id("siteId").build())
                .video(Video.builder().mimes(singletonList("mime")).protocols(singletonList(123)).build()))
                .build();
        givenDataResult(videoRequest, storedIdToImps);
    }

    private void givenDataResult(BidRequestVideo bidRequestVideo, Map<String, Imp> idToImp) {
        try {
            final BidRequest bidRequest = BidRequest.builder().build();
            given(routingContext.getBody()).willReturn(Buffer.buffer(Json.mapper.writeValueAsString(bidRequest)));

            final ParsedStoredDataResult<BidRequestVideo, Imp> dataResult =
                    ParsedStoredDataResult.of(bidRequestVideo, idToImp, emptyList());
            given(storedRequestProcessor.processVideoRequest(anyString(), any(), any()))
                    .willReturn(Future.succeededFuture(dataResult));

            given(auctionRequestFactory.validateRequest(any())).will(invocation -> invocation.getArgument(1));

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private void givenDataResult(BidRequestVideo bidRequestVideo) {
        givenDataResult(bidRequestVideo, emptyMap());
    }
}
