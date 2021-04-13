package org.prebid.server.privacy.gdpr;

import org.prebid.server.auction.BidderAliases;
import org.prebid.server.bidder.BidderCatalog;

public class VendorIdResolver {

    private final BidderAliases aliases;
    private final BidderCatalog bidderCatalog;

    private VendorIdResolver(BidderAliases aliases, BidderCatalog bidderCatalog) {
        this.aliases = aliases;
        this.bidderCatalog = bidderCatalog;
    }

    public static VendorIdResolver of(BidderAliases aliases, BidderCatalog bidderCatalog) {
        return new VendorIdResolver(aliases, bidderCatalog);
    }

    public static VendorIdResolver of(BidderCatalog bidderCatalog) {
        return of(null, bidderCatalog);
    }

    public Integer resolve(String aliasOrBidder) {
        final Integer requestAliasVendorId = aliases != null ? aliases.resolveAliasVendorId(aliasOrBidder) : null;

        return requestAliasVendorId != null ? requestAliasVendorId : resolveViaCatalog(aliasOrBidder);
    }

    private Integer resolveViaCatalog(String aliasOrBidder) {
        final String bidderName = aliases != null ? aliases.resolveBidder(aliasOrBidder) : aliasOrBidder;

        return bidderCatalog.isActive(bidderName) ? bidderCatalog.vendorIdByName(bidderName) : null;
    }
}
