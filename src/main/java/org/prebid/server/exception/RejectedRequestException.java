package org.prebid.server.exception;

import org.prebid.server.auction.model.AuctionContext;

public class RejectedRequestException extends RuntimeException {

    private final AuctionContext auctionContext;

    public RejectedRequestException(AuctionContext auctionContext) {
        this.auctionContext = auctionContext;
    }

    public AuctionContext getAuctionContext() {
        return auctionContext;
    }
}
