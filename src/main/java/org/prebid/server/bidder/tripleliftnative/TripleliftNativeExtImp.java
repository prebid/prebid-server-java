package org.prebid.server.bidder.tripleliftnative;

import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.triplelift.ExtImpTriplelift;

@Value(staticConstructor = "of")
public class TripleliftNativeExtImp {

    ExtImpTriplelift bidder;

    TripleliftNativeExtImpData data;

}
