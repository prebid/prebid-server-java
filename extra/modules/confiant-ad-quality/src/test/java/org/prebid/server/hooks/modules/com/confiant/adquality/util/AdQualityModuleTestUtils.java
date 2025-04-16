package org.prebid.server.hooks.modules.com.confiant.adquality.util;

import com.iab.openrtb.response.Bid;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public class AdQualityModuleTestUtils {

    private AdQualityModuleTestUtils() {
    }

    public static BidderResponse getBidderResponse(String bidderName, String impId, String bidId) {
        return BidderResponse.of(
                bidderName,
                BidderSeatBid.builder()
                        .bids(Collections.singletonList(BidderBid.builder()
                                .type(BidType.banner)
                                .bid(Bid.builder()
                                        .id(bidId)
                                        .price(BigDecimal.valueOf(11))
                                        .impid(impId)
                                        .adm("adm")
                                        .adomain(List.of("www.goog.com", "www.gumgum.com"))
                                        .build())
                                .build()))
                        .build(),
                11);
    }
}
