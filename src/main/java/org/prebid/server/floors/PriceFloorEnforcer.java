package org.prebid.server.floors;

import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.settings.model.Account;

public interface PriceFloorEnforcer {

    AuctionParticipation enforce(AuctionParticipation auctionParticipation, Account account);

    static NoOpPriceFloorEnforcer noOp() {
        return new NoOpPriceFloorEnforcer();
    }

    class NoOpPriceFloorEnforcer implements PriceFloorEnforcer {

        @Override
        public AuctionParticipation enforce(AuctionParticipation auctionParticipation, Account account) {
            return auctionParticipation;
        }
    }
}
