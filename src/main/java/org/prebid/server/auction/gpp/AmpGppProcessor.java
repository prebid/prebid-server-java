package org.prebid.server.auction.gpp;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.model.AuctionContext;

public class AmpGppProcessor {

    public BidRequest process(BidRequest bidRequest, AuctionContext auctionContext) {
        return bidRequest;
    }
}
