package org.prebid.server.auction.model;

public interface Rejected {

    String impId();

    BidRejectionReason reason();

}
