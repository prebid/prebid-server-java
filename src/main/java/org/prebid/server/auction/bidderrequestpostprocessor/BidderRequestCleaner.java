package org.prebid.server.auction.bidderrequestpostprocessor;

import io.vertx.core.Future;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.Result;

import java.util.Collections;

public class BidderRequestCleaner implements BidderRequestPostProcessor {

    @Override
    public Future<Result<BidderRequest>> process(BidderRequest bidderRequest,
                                                 BidderAliases aliases,
                                                 AuctionContext auctionContext) {

        // TODO: implement
        return Future.succeededFuture(Result.of(bidderRequest, Collections.emptyList()));
    }
}
