package org.prebid.server.auction.model;

import lombok.Value;
import lombok.experimental.Accessors;

@Value(staticConstructor = "of")
@Accessors(fluent = true)
public class ImpRejection implements Rejection {

    String seat;

    String impId;

    BidRejectionReason reason;

    public static ImpRejection of(String impId, BidRejectionReason reason) {
        return ImpRejection.of(null, impId, reason);
    }

}
