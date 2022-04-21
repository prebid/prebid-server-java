package org.prebid.server.floors;

import org.prebid.server.auction.model.AuctionContext;

public interface PriceFloorProcessor {

    AuctionContext enrichWithPriceFloors(AuctionContext auctionContext);

    static NoOpPriceFloorProcessor noOp() {
        return new NoOpPriceFloorProcessor();
    }

    class NoOpPriceFloorProcessor implements PriceFloorProcessor {

        @Override
        public AuctionContext enrichWithPriceFloors(AuctionContext auctionContext) {
            return auctionContext;
        }
    }
}
