package org.prebid.server.auction;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.BidderCatalog;

import java.util.Map;
import java.util.Objects;

/**
 * Represents aliases configured for bidders - configuration might come in OpenRTB request but not limited to it.
 */
public class BidderAliases {

    private final Map<String, String> aliasToBidder;

    private final Map<String, Integer> aliasToVendorId;

    private final BidderCatalog bidderCatalog;

    private BidderAliases(
            Map<String, String> aliasToBidder, Map<String, Integer> aliasToVendorId, BidderCatalog bidderCatalog) {

        this.aliasToBidder = MapUtils.emptyIfNull(aliasToBidder);
        this.aliasToVendorId = MapUtils.emptyIfNull(aliasToVendorId);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    public static BidderAliases of(
            Map<String, String> aliasToBidder, Map<String, Integer> aliasToVendorId, BidderCatalog bidderCatalog) {

        return new BidderAliases(aliasToBidder, aliasToVendorId, bidderCatalog);
    }

    public static BidderAliases of(BidderCatalog bidderCatalog) {
        return new BidderAliases(null, null, bidderCatalog);
    }

    public boolean isAliasDefined(String alias) {
        return aliasToBidder.containsKey(alias) || bidderCatalog.isAlias(alias);
    }

    public String resolveBidder(String aliasOrBidder) {
        return aliasToBidder.containsKey(aliasOrBidder)
                ? aliasToBidder.get(aliasOrBidder)
                : ObjectUtils.defaultIfNull(resolveBidderViaCatalog(aliasOrBidder), aliasOrBidder);
    }

    public Integer resolveAliasVendorId(String alias) {
        return aliasToVendorId.containsKey(alias)
                ? aliasToVendorId.get(alias)
                : resolveAliasVendorIdViaCatalog(alias);
    }

    private String resolveBidderViaCatalog(String aliasOrBidder) {
        final String resolvedBidder = bidderCatalog.nameByAlias(aliasOrBidder);
        return bidderCatalog.isActive(resolvedBidder) ? resolvedBidder : null;
    }

    private Integer resolveAliasVendorIdViaCatalog(String alias) {
        final String bidderName = resolveBidder(alias);
        return bidderCatalog.isActive(bidderName) ? bidderCatalog.vendorIdByName(bidderName) : null;
    }
}
