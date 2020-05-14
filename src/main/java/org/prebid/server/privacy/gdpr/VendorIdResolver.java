package org.prebid.server.privacy.gdpr;

import org.prebid.server.auction.BidderAliases;
import org.prebid.server.bidder.BidderCatalog;

import java.util.Objects;

public class VendorIdResolver {

    private final BidderCatalog bidderCatalog;
    private final BidderAliases aliases;

    private VendorIdResolver(BidderCatalog bidderCatalog, BidderAliases aliases) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.aliases = aliases;
    }

    public static VendorIdResolver of(BidderCatalog bidderCatalog, BidderAliases aliases) {
        return new VendorIdResolver(bidderCatalog, aliases);
    }

    public static VendorIdResolver of(BidderCatalog bidderCatalog) {
        return new VendorIdResolver(bidderCatalog, null);
    }

    public Integer resolve(String aliasOrBidder) {
        if (aliases == null) {
            return resolveViaCatalog(aliasOrBidder);
        }

        final Integer aliasVendorId = aliases.resolveAliasVendorId(aliasOrBidder);

        return aliasVendorId != null ? aliasVendorId : resolveViaCatalog(aliases.resolveBidder(aliasOrBidder));

    }

    private Integer resolveViaCatalog(String bidderName) {
        return bidderCatalog.isActive(bidderName) ? bidderCatalog.vendorIdByName(bidderName) : null;
    }
}
