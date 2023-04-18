package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidResponseData;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidsData;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BidsMapperTest {

    @Test
    public void shouldMapBidResponsesToRedisBids() {
        // given
        BidRequest bidRequest = BidRequest.builder()
                .id("bidId")
                .imp(List.of(Imp.builder().id("impId").build()))
                .cur(List.of("EUR"))
                .build();

        BidderResponse bidderResponse1 = getBidderResponse("a", "idA");
        BidderResponse bidderResponse2 = getBidderResponse("b", "idB");

        List<BidderResponse> bidderResponses = List.of(bidderResponse1, bidderResponse2);

        // when
        RedisBidsData result = BidsMapper.bidResponsesToRedisBids(bidRequest, bidderResponses);

        // then
        assertThat(result.getBreq()).isEqualTo(bidRequest);
        assertThat(result.getBresps()).hasSize(2);

        RedisBidResponseData redisBidResponseData1 = result.getBresps().get(0);
        assertThat(redisBidResponseData1.getDspId()).isEqualTo(bidderResponse1.getBidder());
        assertThat(redisBidResponseData1.getBidresponse().getId()).isEqualTo(bidRequest.getId());
        assertThat(redisBidResponseData1.getBidresponse().getCur()).isEqualTo(bidRequest.getCur().get(0));
        assertThat(redisBidResponseData1.getBidresponse().getSeatbid()).hasSize(1);
        SeatBid seatBid1 = redisBidResponseData1.getBidresponse().getSeatbid().get(0);
        assertThat(seatBid1.getBid()).hasSize(1);
        assertThat(seatBid1.getBid().get(0).getId()).isEqualTo(bidderResponse1.getSeatBid().getBids().get(0).getBid().getId());

        RedisBidResponseData redisBidResponseData2 = result.getBresps().get(1);
        assertThat(redisBidResponseData2.getDspId()).isEqualTo(bidderResponse2.getBidder());
        assertThat(redisBidResponseData2.getBidresponse().getId()).isEqualTo(bidRequest.getId());
        assertThat(redisBidResponseData2.getBidresponse().getCur()).isEqualTo(bidRequest.getCur().get(0));
        assertThat(redisBidResponseData2.getBidresponse().getSeatbid()).hasSize(1);
        SeatBid seatBid2 = redisBidResponseData2.getBidresponse().getSeatbid().get(0);
        assertThat(seatBid2.getBid()).hasSize(1);
        assertThat(seatBid2.getBid().get(0).getId()).isEqualTo(bidderResponse2.getSeatBid().getBids().get(0).getBid().getId());
    }

    private BidderResponse getBidderResponse(String bidderName, String bidId) {
        return BidderResponse.of(bidderName, BidderSeatBid.builder()
                .bids(Collections.singletonList(BidderBid.builder()
                        .type(BidType.banner)
                        .bid(Bid.builder()
                                .id(bidId)
                                .price(BigDecimal.valueOf(11))
                                .impid("1")
                                .adm("adm")
                                .adomain(Collections.singletonList("www.example.com"))
                                .build())
                        .build()))
                .build(), 11);
    }
}
