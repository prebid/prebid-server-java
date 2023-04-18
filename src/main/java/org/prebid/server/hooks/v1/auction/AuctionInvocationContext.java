package org.prebid.server.hooks.v1.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.auction.model.AuctionContext;
import com.iab.openrtb.request.BidRequest;
import org.prebid.server.hooks.v1.InvocationContext;

public interface AuctionInvocationContext extends InvocationContext {

    AuctionContext auctionContext();

    Object moduleContext();

    boolean debugEnabled();

    ObjectNode accountConfig();

    BidRequest bidRequest();
}
