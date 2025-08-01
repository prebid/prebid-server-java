package org.prebid.server.auction.model;

import lombok.Value;
import lombok.experimental.Accessors;

@Value(staticConstructor = "of")
@Accessors(fluent = true)
public class RejectedImp implements Rejected {

    String seat;

    String impId;

    BidRejectionReason reason;

    public static RejectedImp of(String impId, BidRejectionReason reason) {
        return RejectedImp.of(null, impId, reason);
    }

}
