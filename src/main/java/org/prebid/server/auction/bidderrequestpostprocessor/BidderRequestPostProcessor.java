package org.prebid.server.auction.bidderrequestpostprocessor;

import io.vertx.core.Future;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderRequest;

public interface BidderRequestPostProcessor {

    Future<BidderRequestPostProcessingResult> process(BidderRequest bidderRequest,
                                                      BidderAliases aliases,
                                                      AuctionContext auctionContext);
}
