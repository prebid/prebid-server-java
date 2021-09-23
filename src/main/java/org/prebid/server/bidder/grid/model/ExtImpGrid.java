package org.prebid.server.bidder.grid.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.grid.ExtImpGridBidder;

@Value
@Builder(toBuilder = true)
public class ExtImpGrid {

    ExtImpPrebid prebid;

    ExtImpGridBidder bidder;

    ExtImpGridData data;

    String gpid;
}

