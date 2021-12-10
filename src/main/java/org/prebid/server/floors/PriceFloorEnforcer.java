package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.settings.model.Account;

public interface PriceFloorEnforcer {

    AuctionParticipation enforce(BidRequest bidRequest,
                                 AuctionParticipation auctionParticipation,
                                 Account account);

    static NoOpPriceFloorEnforcer noOp() {
        return new NoOpPriceFloorEnforcer();
    }

    class NoOpPriceFloorEnforcer implements PriceFloorEnforcer {

        @Override
        public AuctionParticipation enforce(BidRequest bidRequest,
                                            AuctionParticipation auctionParticipation,
                                            Account account) {

            return auctionParticipation;
        }
    }
}
