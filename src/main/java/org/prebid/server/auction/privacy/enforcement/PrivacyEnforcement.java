package org.prebid.server.auction.privacy.enforcement;

import io.vertx.core.Future;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderPrivacyResult;

import java.util.List;

public interface PrivacyEnforcement {

    Future<List<BidderPrivacyResult>> enforce(AuctionContext auctionContext,
                                              BidderAliases aliases,
                                              List<BidderPrivacyResult> results);
}
