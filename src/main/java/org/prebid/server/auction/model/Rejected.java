package org.prebid.server.auction.model;

public interface Rejected {

    String seat();

    String impId();

    BidRejectionReason reason();

}
