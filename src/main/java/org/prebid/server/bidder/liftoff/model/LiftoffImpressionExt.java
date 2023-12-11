package org.prebid.server.bidder.liftoff.model;

import lombok.Builder;
import lombok.Getter;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.liftoff.ExtImpLiftoff;

@Builder(toBuilder = true)
@Getter
public class LiftoffImpressionExt {

    ExtImpPrebid prebid;

    ExtImpLiftoff bidder;

    ExtImpLiftoff vungle;
}
