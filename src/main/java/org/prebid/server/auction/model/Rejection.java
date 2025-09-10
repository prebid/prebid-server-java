package org.prebid.server.auction.model;

public interface Rejection {

    String seat();

    String impId();

    BidRejectionReason reason();

}
