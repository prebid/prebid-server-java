package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.request.video.BidRequestVideo;
import com.iab.openrtb.request.video.CacheConfig;
import com.iab.openrtb.request.video.Pod;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.request.video.Podconfig;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCache;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidCacheVastxml;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.validation.VideoRequestValidator;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.function.UnaryOperator;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class VideoStoredRequestProcessorTest extends VertxTest {

    private static final String STORED_REQUEST_ID = "storedReqId";
    private static final String STORED_POD_ID = "storedPodId";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FileSystem fileSystem;
    @Mock
    private ApplicationSettings applicationSettings;
    @Mock
    private VideoRequestValidator validator;
    @Mock
    private Metrics metrics;
    @Mock
    private TimeoutResolver timeoutResolver;
    @Mock
    private TimeoutFactory timeoutFactory;

    private VideoStoredRequestProcessor target;

    @Before
    public void setUp() throws JsonProcessingException {
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer(mapper.writeValueAsString(BidRequest.builder().at(1).build())));

        target = VideoStoredRequestProcessor.create(
                false,
                emptyList(),
                2000L,
                "USD",
                "path/to/default/request.json",
                fileSystem,
                applicationSettings,
                validator,
                metrics,
                timeoutFactory,
                timeoutResolver,
                jacksonMapper,
                new JsonMerger(jacksonMapper));
    }

    @Test
    public void shouldReturnFailedFutureWhenFetchStoredIsFailed() {
        // given
        given(applicationSettings.getVideoStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.failedFuture("ERROR"));

        // when
        final Future<WithPodErrors<BidRequest>> result = target.processVideoRequest(null, STORED_REQUEST_ID,
                singleton(STORED_POD_ID), null);

        // then
        assertThat(result.cause()).hasMessageStartingWith("Stored request fetching failed: ERROR");
    }

    @Test
    public void shouldReturnFutureWithMergedStoredAndDefaultRequest() {
        // given
        final User user = User.builder()
                .yob(123)
                .buyeruid("value")
                .gender("gender")
                .keywords("keywords")
                .build();
        final Content content = Content.builder()
                .len(900)
                .livestream(0)
                .build();

        final BidRequestVideo storedVideo = givenValidDataResult(builder -> builder
                        .user(user)
                        .content(content)
                        .cacheconfig(CacheConfig.of(42))
                        .bcat(singletonList("bcat"))
                        .badv(singletonList("badv")),
                UnaryOperator.identity());

        final BidRequestVideo requestVideo = givenValidDataResult(
                UnaryOperator.identity(),
                builder -> builder.pods(singletonList(Pod.of(123, 20, STORED_POD_ID))));

        final StoredDataResult storedDataResult = StoredDataResult.of(
                singletonMap(STORED_REQUEST_ID, jacksonMapper.encode(storedVideo)),
                singletonMap(STORED_POD_ID, "{}"),
                emptyList());

        given(applicationSettings.getVideoStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(storedDataResult));
        given(validator.validPods(any(), any()))
                .willReturn(WithPodErrors.of(singletonList(Pod.of(123, 20, STORED_POD_ID)), emptyList()));

        // when
        final Future<WithPodErrors<BidRequest>> result = target.processVideoRequest(null, STORED_REQUEST_ID,
                singleton(STORED_POD_ID), requestVideo);

        // then
        verify(applicationSettings).getVideoStoredData(any(), eq(singleton(STORED_REQUEST_ID)),
                eq(singleton(STORED_POD_ID)), any());

        verify(metrics).updateStoredRequestMetric(true);
        verify(metrics).updateStoredImpsMetric(true);

        verify(validator).validateStoredBidRequest(any(), anyBoolean(), any());
        verify(validator).validPods(any(), eq(singleton(STORED_POD_ID)));

        final Imp expectedImp1 = Imp.builder()
                .id("123_0")
                .video(Video.builder()
                        .mimes(singletonList("mime"))
                        .maxduration(200)
                        .protocols(singletonList(123))
                        .build())
                .build();
        final Imp expectedImp2 = Imp.builder()
                .id("123_1")
                .video(Video.builder()
                        .mimes(singletonList("mime"))
                        .maxduration(200)
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

        final BidRequest expectedMergedRequest = BidRequest.builder()
                .id("bid_id")
                .at(1)
                .imp(asList(expectedImp1, expectedImp2))
                .user(User.builder().buyeruid("value").yob(123).gender("gender").keywords("keywords").build())
                .site(Site.builder().id("siteId").content(content).build())
                .bcat(singletonList("bcat"))
                .badv(singletonList("badv"))
                .cur(singletonList("USD"))
                .tmax(0L)
                .ext(ExtRequest.of(ext))
                .build();

        assertThat(result.result()).isEqualTo(WithPodErrors.of(expectedMergedRequest, emptyList()));
    }

    @Test
    public void processVideoRequestShouldFailWhenThereAreNoStoredImpsFound() {
        // given
        final Pod pod1 = Pod.of(1, 2, "333");
        final Pod pod2 = Pod.of(2, 3, "222");
        final BidRequestVideo requestVideo = givenValidDataResult(
                UnaryOperator.identity(),
                builder -> builder.pods(asList(pod1, pod2)));

        final StoredDataResult storedDataResult = StoredDataResult.of(emptyMap(), emptyMap(), emptyList());

        given(applicationSettings.getVideoStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(storedDataResult));

        final PodError podError1 = PodError.of(1, 1, singletonList("ERROR1"));
        final PodError podError2 = PodError.of(2, 2, singletonList("ERROR2"));

        given(validator.validPods(any(), any()))
                .willReturn(WithPodErrors.of(emptyList(), asList(podError1, podError2)));

        // when
        final Future<WithPodErrors<BidRequest>> result = target.processVideoRequest(null, STORED_REQUEST_ID,
                singleton(STORED_POD_ID), requestVideo);

        // then
        verify(metrics, never()).updateStoredRequestMetric(true);
        verify(metrics, never()).updateStoredImpsMetric(true);

        verify(validator).validateStoredBidRequest(any(), anyBoolean(), any());

        final Podconfig expectedPodConfig = Podconfig.builder()
                .durationRangeSec(asList(200, 100))
                .pods(asList(pod1, pod2))
                .build();
        verify(validator).validPods(eq(expectedPodConfig), eq(emptySet()));

        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Stored request fetching failed: all pods are incorrect:  ERROR1; ERROR2");
    }

    @Test
    public void shouldReturnFutureWithCorrectAdPodDurationIfRequireExactDurationIsTrue() {
        // given
        final BidRequestVideo storedVideo = givenValidDataResult(builder -> builder
                        .cacheconfig(CacheConfig.of(42)),
                UnaryOperator.identity());

        final BidRequestVideo requestVideo = givenValidDataResult(
                UnaryOperator.identity(),
                builder -> builder.requireExactDuration(true)
                        .durationRangeSec(Arrays.asList(30, 60, 80))
                        .pods(singletonList(Pod.of(123, 30, STORED_POD_ID))));

        final StoredDataResult storedDataResult = StoredDataResult.of(
                singletonMap(STORED_REQUEST_ID, jacksonMapper.encode(storedVideo)),
                singletonMap(STORED_POD_ID, "{}"),
                emptyList());

        given(applicationSettings.getVideoStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(storedDataResult));
        given(validator.validPods(any(), any()))
                .willReturn(WithPodErrors.of(singletonList(Pod.of(123, 20, STORED_POD_ID)), emptyList()));

        // when
        final Future<WithPodErrors<BidRequest>> result = target.processVideoRequest(null, STORED_REQUEST_ID,
                singleton(STORED_POD_ID), requestVideo);

        // then
        verify(applicationSettings).getVideoStoredData(any(), eq(singleton(STORED_REQUEST_ID)),
                eq(singleton(STORED_POD_ID)), any());

        verify(metrics).updateStoredRequestMetric(true);
        verify(metrics).updateStoredImpsMetric(true);

        verify(validator).validateStoredBidRequest(any(), anyBoolean(), any());
        verify(validator).validPods(any(), eq(singleton(STORED_POD_ID)));

        final Imp expectedImp1 = Imp.builder()
                .id("123_0")
                .video(Video.builder()
                        .mimes(singletonList("mime"))
                        .minduration(30)
                        .maxduration(30)
                        .protocols(singletonList(123))
                        .build())
                .build();
        final Imp expectedImp2 = Imp.builder()
                .id("123_1")
                .video(Video.builder()
                        .mimes(singletonList("mime"))
                        .minduration(60)
                        .maxduration(60)
                        .protocols(singletonList(123))
                        .build())
                .build();
        final Imp expectedImp3 = Imp.builder()
                .id("123_2")
                .video(Video.builder()
                        .mimes(singletonList("mime"))
                        .minduration(80)
                        .maxduration(80)
                        .protocols(singletonList(123))
                        .build())
                .build();

        assertThat(result.result().getData().getImp())
                .isEqualTo(Arrays.asList(expectedImp1, expectedImp2, expectedImp3));
    }

    @Test
    public void shouldReturnFutureWithCorrectPriceGranularityInRequest() {
        // given
        final BidRequestVideo storedVideo = givenValidDataResult(builder -> builder
                        .cacheconfig(CacheConfig.of(42))
                        .bcat(singletonList("bcat"))
                        .badv(singletonList("badv")),
                UnaryOperator.identity());
        final PriceGranularity priceGranularity = PriceGranularity.createFromExtPriceGranularity(
                ExtPriceGranularity.of(1,
                        singletonList(ExtGranularityRange.of(new BigDecimal(10), new BigDecimal("0.5"))))
        );

        final BidRequestVideo requestVideo = givenValidDataResult(
                bidRequestVideoBuilder -> bidRequestVideoBuilder.pricegranularity(priceGranularity),
                builder -> builder.pods(singletonList(Pod.of(123, 20, STORED_POD_ID))));

        final StoredDataResult storedDataResult = StoredDataResult.of(
                singletonMap(STORED_REQUEST_ID, jacksonMapper.encode(storedVideo)),
                singletonMap(STORED_POD_ID, "{}"),
                emptyList());

        given(applicationSettings.getVideoStoredData(any(), anySet(), anySet(), any()))
                .willReturn(Future.succeededFuture(storedDataResult));
        given(validator.validPods(any(), any()))
                .willReturn(WithPodErrors.of(singletonList(Pod.of(123, 20, STORED_POD_ID)), emptyList()));

        // when
        final Future<WithPodErrors<BidRequest>> result = target.processVideoRequest(null, STORED_REQUEST_ID,
                singleton(STORED_POD_ID), requestVideo);

        // then
        verify(applicationSettings).getVideoStoredData(any(), eq(singleton(STORED_REQUEST_ID)),
                eq(singleton(STORED_POD_ID)), any());

        verify(metrics).updateStoredRequestMetric(true);
        verify(metrics).updateStoredImpsMetric(true);

        verify(validator).validateStoredBidRequest(any(), anyBoolean(), any());
        verify(validator).validPods(any(), eq(singleton(STORED_POD_ID)));

        assertThat(result.result().getData().getExt().getPrebid().getTargeting().getPricegranularity())
                .isEqualTo(mapper.valueToTree(priceGranularity));
    }

    private BidRequestVideo givenValidDataResult(
            UnaryOperator<BidRequestVideo.BidRequestVideoBuilder> requestCustomizer,
            UnaryOperator<Podconfig.PodconfigBuilder> podconfigCustomizer) {

        return requestCustomizer.apply(BidRequestVideo.builder()
                        .storedrequestid("storedrequestid")
                        .podconfig(podconfigCustomizer.apply(Podconfig.builder()
                                        .durationRangeSec(asList(200, 100)))
                                .build())
                        .site(Site.builder().id("siteId").build())
                        .video(Video.builder().mimes(singletonList("mime")).protocols(singletonList(123)).build()))
                .build();
    }
}
