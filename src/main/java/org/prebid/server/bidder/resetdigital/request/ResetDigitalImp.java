package org.prebid.server.bidder.resetdigital.request;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ResetDigitalImp {

    ResetDigitalImpZone zoneId;

    String bidId;

    String impId;

    ResetDigitalImpMediaTypes mediaTypes;

    ResetDigitalImpExt ext;
}
