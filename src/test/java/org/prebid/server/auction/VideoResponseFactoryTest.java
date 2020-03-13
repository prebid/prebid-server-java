package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtAdPod;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtResponseVideoTargeting;
import org.prebid.server.proto.response.VideoResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class VideoResponseFactoryTest extends VertxTest {

    private VideoResponseFactory target;

    @Before
    public void setUp() {
        target = new VideoResponseFactory(jacksonMapper);
    }

    @Test
    public void shouldReturnExpectedVideoResponse() {
        // given
        final Map<String, String> targeting = new HashMap<>();
        targeting.put("hb_uuid", "value1");
        targeting.put("hb_pb", "hb_pb");
        targeting.put("hb_pb_cat_dur", "hb_pb_cat_dur");

        final Bid bid0 = Bid.builder()
                .impid("0_0")
                .ext(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, targeting, null, null, null, null),
                                mapper.createObjectNode())))
                .build();
        final Bid bid1 = Bid.builder()
                .impid("1_1")
                .ext(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, targeting, null, null, null, null),
                                mapper.createObjectNode())))
                .build();
        final Bid bid2 = Bid.builder()
                .impid("2_1")
                .ext(mapper.valueToTree(
                        ExtPrebid.of(ExtBidPrebid.of(null, null, null, null, null, null), mapper.createObjectNode())))
                .build();

        final BidResponse bidResponse = BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .seat("bidder1")
                        .bid(Arrays.asList(bid0, bid1, bid2))
                        .build()))
                .build();

        final PodError podError = PodError.of(3, 1, singletonList("Error"));

        // when
        final VideoResponse result = target.toVideoResponse(BidRequest.builder().build(), bidResponse,
                singletonList(podError));

        // then
        final ExtAdPod expectedExtAdPod0 = ExtAdPod.of(0,
                singletonList(ExtResponseVideoTargeting.of("hb_pb", "hb_pb_cat_dur", "value1")), null);
        final ExtAdPod expectedExtAdPod1 = ExtAdPod.of(
                1, singletonList(ExtResponseVideoTargeting.of("hb_pb", "hb_pb_cat_dur", "value1")), null);
        final ExtAdPod expectedErrorExtAdPod3 = ExtAdPod.of(3, null, singletonList("Error"));
        final List<ExtAdPod> expectedAdPodResponse = Arrays.asList(expectedExtAdPod0, expectedExtAdPod1,
                expectedErrorExtAdPod3);

        final VideoResponse videoResponse = VideoResponse.of(expectedAdPodResponse, null, null, null);

        assertThat(result).isEqualTo(videoResponse);
    }
}
