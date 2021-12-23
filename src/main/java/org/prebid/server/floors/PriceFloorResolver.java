package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.floors.model.PriceFloorModelGroup;
import org.prebid.server.floors.model.PriceFloorResult;

public interface PriceFloorResolver {

    PriceFloorResult resolve(BidRequest bidRequest, PriceFloorModelGroup modelGroup, Imp imp, String currency);
}
