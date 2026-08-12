package org.prebid.server.bidder.magnite.proto.request;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;

@Value(staticConstructor = "of")
public class MagniteImpExtPrebid {

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.floors
     */
    ExtImpPrebidFloors floors;
}
