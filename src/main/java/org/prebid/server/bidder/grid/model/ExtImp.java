package org.prebid.server.bidder.grid.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.grid.ExtImpGrid;

@Value
@Builder(toBuilder = true)
public class ExtImp {

    ExtImpPrebid prebid;

    ExtImpGrid bidder;

    ExtImpGridData data;

    String gpid;
}

