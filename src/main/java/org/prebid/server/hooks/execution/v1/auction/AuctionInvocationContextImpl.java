package org.prebid.server.hooks.execution.v1.auction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class AuctionInvocationContextImpl implements AuctionInvocationContext {

    @Delegate
    InvocationContext invocationContext;

    AuctionContext auctionContext;

    boolean debugEnabled;

    ObjectNode accountConfig;

    Object moduleContext;

    BidRequest bidRequest;
}
