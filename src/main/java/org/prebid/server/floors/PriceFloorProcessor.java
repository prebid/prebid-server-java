package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import org.prebid.server.settings.model.Account;

import java.util.List;

public interface PriceFloorProcessor {

    BidRequest enrichWithPriceFloors(BidRequest bidRequest,
                                     Account account,
                                     String bidder,
                                     List<String> errors,
                                     List<String> warnings);

    static NoOpPriceFloorProcessor noOp() {
        return new NoOpPriceFloorProcessor();
    }

    class NoOpPriceFloorProcessor implements PriceFloorProcessor {

        @Override
        public BidRequest enrichWithPriceFloors(BidRequest bidRequest,
                                                Account account,
                                                String bidder,
                                                List<String> errors,
                                                List<String> warnings) {

            return bidRequest;
        }
    }
}
