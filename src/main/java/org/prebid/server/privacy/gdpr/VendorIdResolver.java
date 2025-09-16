package org.prebid.server.privacy.gdpr;

import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.bidder.BidderCatalog;

public class VendorIdResolver {

    private final BidderAliases aliases;

    private VendorIdResolver(BidderAliases aliases) {
        this.aliases = aliases;
    }

    public static VendorIdResolver of(BidderAliases aliases) {
        return new VendorIdResolver(aliases);
    }

    public static VendorIdResolver of(BidderCatalog bidderCatalog) {
        return of(BidderAliases.of(null, null, bidderCatalog));
    }

    public Integer resolve(String aliasOrBidder) {
        return aliases != null ? aliases.resolveAliasVendorId(aliasOrBidder) : null;
    }
}
