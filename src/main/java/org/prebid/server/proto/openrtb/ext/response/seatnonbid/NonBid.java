package org.prebid.server.proto.openrtb.ext.response.seatnonbid;

import lombok.Value;
import org.prebid.server.auction.model.ImpRejectionReason;

@Value(staticConstructor = "of")
public class NonBid {

    String impId;

    ImpRejectionReason reason;
}
