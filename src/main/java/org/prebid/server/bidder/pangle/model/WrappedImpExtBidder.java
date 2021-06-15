package org.prebid.server.bidder.pangle.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.pangle.ExtImpPangle;

@AllArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
@Value
public class WrappedImpExtBidder {

    ExtImpPrebid prebid;

    ExtImpPangle bidder;

    @JsonProperty("adtype")
    Integer adType;

    Boolean isPrebid;

    NetworkIds networkids;
}
