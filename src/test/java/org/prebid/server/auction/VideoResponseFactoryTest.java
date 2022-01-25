package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.CachedDebugLog;
import org.prebid.server.auction.model.DebugContext;
import org.prebid.server.identity.UUIDIdGenerator;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtAdPod;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponsePrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtModules;
import org.prebid.server.proto.openrtb.ext.response.ExtModulesTrace;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseVideoTargeting;
import org.prebid.server.proto.response.ExtAmpVideoPrebid;
import org.prebid.server.proto.response.ExtAmpVideoResponse;
import org.prebid.server.proto.response.VideoResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class VideoResponseFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UUIDIdGenerator uuidIdGenerator;

    private VideoResponseFactory target;

    @Before
    public void setUp() {
        target = new VideoResponseFactory(uuidIdGenerator, jacksonMapper);
    }

    @Test
    public void shouldUpdateCachedDebugLogAndResponseWhenZeroAdPods() {
        // given
        final BidResponse bidResponse = BidResponse.builder().seatbid(emptyList()).build();
        final CachedDebugLog cachedDebugLog = new CachedDebugLog(true, 100, null, jacksonMapper);
        final AuctionContext auctionContext = AuctionContext.builder()
                .debugContext(DebugContext.of(true, null))
                .cachedDebugLog(cachedDebugLog)
                .bidRequest(BidRequest.builder().build())
                .build();
        given(uuidIdGenerator.generateId()).willReturn("generatedId");

        // when
        final VideoResponse result = target.toVideoResponse(auctionContext, bidResponse, emptyList());

        // then
        assertThat(result.getAdPods()).hasSize(1)
                .extracting(ExtAdPod::getTargeting)
                .hasSize(1)
                .extracting(extResponseVideoTargetings -> extResponseVideoTargetings.get(0))
                .extracting(ExtResponseVideoTargeting::getHbCacheID)
                .containsOnly("generatedId");
        assertThat(cachedDebugLog.hasBids()).isFalse();
        assertThat(cachedDebugLog.getCacheKey()).isEqualTo("generatedId");
    }

    @Test
    public void shouldReturnExpectedVideoResponse() {
        // given
        final Map<String, String> targeting = new HashMap<>();
        targeting.put("hb_uuid_appnexus", "hb_uuidVal");
        targeting.put("hb_pb_appnexus", "hb_pbVal");
        targeting.put("hb_pb_cat_dur_appnexus", "hb_pb_cat_durVal");

        final Bid bid0 = Bid.builder()
                .impid("0_0")
                .ext(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.builder().targeting(targeting).build(),
                                mapper.createObjectNode())))
                .build();
        final Bid bid1 = Bid.builder()
                .impid("1_1")
                .ext(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.builder().targeting(targeting).build(),
                                mapper.createObjectNode())))
                .build();
        final Bid bid2 = Bid.builder()
                .impid("2_1")
                .ext(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.builder().build(),
                                mapper.createObjectNode())))
                .build();

        final ExtBidResponse extResponse = ExtBidResponse.builder()
                .prebid(ExtBidResponsePrebid.of(
                        1000L,
                        ExtModules.of(
                                singletonMap(
                                        "module1", singletonMap("hook1", singletonList("error1"))),
                                singletonMap(
                                        "module1", singletonMap("hook1", singletonList("warning1"))),
                                ExtModulesTrace.of(2L, emptyList()))))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .seat("bidder1")
                        .bid(Arrays.asList(bid0, bid1, bid2))
                        .build()))
                .ext(extResponse)
                .build();

        final PodError podError = PodError.of(3, 1, singletonList("Error"));

        // when
        final VideoResponse result = target.toVideoResponse(
                AuctionContext.builder()
                        .bidRequest(BidRequest.builder().build())
                        .debugContext(DebugContext.empty())
                        .build(),
                bidResponse,
                singletonList(podError));

        // then
        final ExtAdPod expectedExtAdPod0 = ExtAdPod.of(0,
                singletonList(ExtResponseVideoTargeting.of("hb_pbVal", "hb_pb_cat_durVal", "hb_uuidVal")), null);
        final ExtAdPod expectedExtAdPod1 = ExtAdPod.of(
                1, singletonList(ExtResponseVideoTargeting.of("hb_pbVal", "hb_pb_cat_durVal", "hb_uuidVal")), null);
        final ExtAdPod expectedErrorExtAdPod3 = ExtAdPod.of(3, null, singletonList("Error"));
        final List<ExtAdPod> expectedAdPodResponse = Arrays.asList(expectedExtAdPod0, expectedExtAdPod1,
                expectedErrorExtAdPod3);

        assertThat(result).isEqualTo(VideoResponse.of(
                expectedAdPodResponse,
                ExtAmpVideoResponse.of(
                        bidResponse.getExt().getDebug(),
                        bidResponse.getExt().getErrors(),
                        bidResponse.getExt().getWarnings(),
                        ExtAmpVideoPrebid.of(extResponse.getPrebid().getModules()))));
    }
}
