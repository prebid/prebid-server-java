package org.prebid.server.proto.openrtb.ext.request.grid;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;

@Value
@Builder(toBuilder = true)
public class ExtImpGrid {

    ExtImpPrebid prebid;

    ExtImpGridBidder bidder;

    ExtImpGridData data;

    String gpid;
}

