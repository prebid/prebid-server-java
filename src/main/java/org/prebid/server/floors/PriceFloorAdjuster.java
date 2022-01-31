package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;

public interface PriceFloorAdjuster {

    BigDecimal adjustForImp(Imp imp, String bidder, BidRequest bidRequest);

    static NoOpPriceFloorAdjuster noOp() {
        return new NoOpPriceFloorAdjuster();
    }

    class NoOpPriceFloorAdjuster implements PriceFloorAdjuster {

        @Override
        public BigDecimal adjustForImp(Imp imp, String bidder, BidRequest bidRequest) {
            return ObjectUtil.getIfNotNull(imp, Imp::getBidfloor);
        }
    }
}
