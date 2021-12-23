package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;

public class BasicPriceFloorResolver implements PriceFloorResolver {

    @Override
    public PriceFloorResult resolve(BidRequest bidRequest, PriceFloorModelGroup modelGroup, Imp imp, String currency) {
        return PriceFloorResult.empty();
    }
}
