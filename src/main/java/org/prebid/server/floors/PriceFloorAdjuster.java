package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.ObjectUtil;

import java.util.List;

public interface PriceFloorAdjuster {

    Price adjustForImp(Imp imp, String bidder, BidRequest bidRequest, Account account, List<String> debugWarnings);

    Price revertAdjustmentForImp(Imp imp, String bidder, BidRequest bidRequest, Account account);

    static NoOpPriceFloorAdjuster noOp() {
        return new NoOpPriceFloorAdjuster();
    }

    class NoOpPriceFloorAdjuster implements PriceFloorAdjuster {

        @Override
        public Price adjustForImp(Imp imp,
                                  String bidder,
                                  BidRequest bidRequest,
                                  Account account,
                                  List<String> debugWarnings) {

            return ObjectUtil.getIfNotNull(imp, i -> Price.of(i.getBidfloorcur(), i.getBidfloor()));
        }

        @Override
        public Price revertAdjustmentForImp(Imp imp, String bidder, BidRequest bidRequest, Account account) {
            return ObjectUtil.getIfNotNull(imp, i -> Price.of(i.getBidfloorcur(), i.getBidfloor()));
        }
    }
}
