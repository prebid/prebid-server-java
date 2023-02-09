package org.prebid.server.auction.gpp;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.model.AuctionContext;

public class AuctionGppProcessor {

    public BidRequest process(BidRequest bidRequest, AuctionContext auctionContext) {
        return bidRequest;
    }
}
