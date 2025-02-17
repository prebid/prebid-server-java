package org.prebid.server.auction.model;

import lombok.Value;
import lombok.experimental.Accessors;

@Value(staticConstructor = "of")
@Accessors(fluent = true)
public class RejectedImp implements Rejected {

    String impId;

    BidRejectionReason reason;

}
