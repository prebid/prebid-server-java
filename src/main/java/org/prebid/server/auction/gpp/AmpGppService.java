package org.prebid.server.auction.gpp;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.model.AuctionContext;

public class AmpGppService {

    private final GppService gppService;

    public AmpGppService(GppService gppService) {
        this.gppService = gppService;
    }

    public BidRequest apply(BidRequest bidRequest, AuctionContext auctionContext) {
        return bidRequest;
    }
}
