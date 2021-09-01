package org.prebid.server.bidder.grid.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;

@Builder(toBuilder = true)
@Value
public class ExtImpGrid {

    ExtImpPrebid prebid;

    ExtImpGridBidder bidder;

    ExtImpGridData data;

    String gpid;
}
