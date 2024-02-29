package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidResponseData;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidsData;
import org.prebid.server.hooks.modules.com.confiant.adquality.util.AdQualityModuleTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BidsMapperTest {

    @Test
    public void shouldMapBidResponsesToRedisBids() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .id("bidId")
                .imp(List.of(Imp.builder().id("impId").build()))
                .cur(List.of("EUR"))
                .build();

        final BidderResponse bidderResponse1 = AdQualityModuleTestUtils.getBidderResponse("a", "idA", "bidIdA");
        final BidderResponse bidderResponse2 = AdQualityModuleTestUtils.getBidderResponse("b", "idB", "bidIdB");

        final List<BidderResponse> bidderResponses = List.of(bidderResponse1, bidderResponse2);

        // when
        final RedisBidsData result = BidsMapper.toRedisBidsFromBidResponses(bidRequest, bidderResponses);

        // then
        assertThat(result.getBreq()).isEqualTo(bidRequest);
        assertThat(result.getBresps()).hasSize(2);

        final RedisBidResponseData redisBidResponseData1 = result.getBresps().get(0);
        assertThat(redisBidResponseData1.getDspId()).isEqualTo(bidderResponse1.getBidder());
        assertThat(redisBidResponseData1.getBidresponse().getId()).isEqualTo(bidRequest.getId());
        assertThat(redisBidResponseData1.getBidresponse().getCur()).isEqualTo(bidRequest.getCur().get(0));
        assertThat(redisBidResponseData1.getBidresponse().getSeatbid()).hasSize(1);
        SeatBid seatBid1 = redisBidResponseData1.getBidresponse().getSeatbid().get(0);
        assertThat(seatBid1.getBid()).hasSize(1);
        assertThat(seatBid1.getBid().get(0).getId()).isEqualTo(bidderResponse1.getSeatBid().getBids().get(0).getBid().getId());

        final RedisBidResponseData redisBidResponseData2 = result.getBresps().get(1);
        assertThat(redisBidResponseData2.getDspId()).isEqualTo(bidderResponse2.getBidder());
        assertThat(redisBidResponseData2.getBidresponse().getId()).isEqualTo(bidRequest.getId());
        assertThat(redisBidResponseData2.getBidresponse().getCur()).isEqualTo(bidRequest.getCur().get(0));
        assertThat(redisBidResponseData2.getBidresponse().getSeatbid()).hasSize(1);

        final SeatBid seatBid2 = redisBidResponseData2.getBidresponse().getSeatbid().get(0);
        assertThat(seatBid2.getBid()).hasSize(1);
        assertThat(seatBid2.getBid().get(0).getId()).isEqualTo(bidderResponse2.getSeatBid().getBids().get(0).getBid().getId());
    }
}
