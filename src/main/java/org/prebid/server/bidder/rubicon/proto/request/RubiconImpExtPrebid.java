package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebidFloors;

@Value(staticConstructor = "of")
public class RubiconImpExtPrebid {

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.floors
     */
    ExtImpPrebidFloors floors;
}
