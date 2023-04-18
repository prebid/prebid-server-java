package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidResponseData;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidsData;

import java.util.Collections;
import java.util.List;

public class BidsMapper {

    public static RedisBidsData bidResponsesToRedisBids(
            BidRequest bidRequest,
            List<BidderResponse> bidderResponses
    ) {
        final List<RedisBidResponseData> confiantBidResponses = bidderResponses
                .stream().map(bidResponse -> RedisBidResponseData
                        .builder()
                        .dspId(bidResponse.getBidder())
                        .bidresponse(BidResponse.builder()
                                .id(bidRequest.getId())
                                .cur(bidRequest.getCur().get(0))
                                .seatbid(Collections.singletonList(SeatBid.builder()
                                        .bid(bidResponse.getSeatBid().getBids().stream().map(BidderBid::getBid).toList())
                                        .build()))
                                .build())
                        .build()
                ).toList();

        return RedisBidsData.builder()
                .breq(bidRequest)
                .bresps(confiantBidResponses)
                .build();
    }
}
