package org.prebid.server.bidder.liftoff.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.liftoff.ExtImpLiftoff;

@Builder(toBuilder = true)
@Getter
public class LiftoffImpressionExt {

    ExtImpPrebid prebid;

    ExtImpLiftoff bidder;

    @JsonProperty("vungle")
    ExtImpLiftoff vungle;
}
