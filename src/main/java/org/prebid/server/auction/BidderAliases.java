package org.prebid.server.auction;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
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

    private BidderAliases(Map<String, String> aliasToBidder,
                          Map<String, Integer> aliasToVendorId,
                          BidderCatalog bidderCatalog) {

        this.aliasToBidder = MapUtils.emptyIfNull(aliasToBidder);
        this.aliasToVendorId = MapUtils.emptyIfNull(aliasToVendorId);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    public static BidderAliases of(Map<String, String> aliasToBidder,
                                   Map<String, Integer> aliasToVendorId,
                                   BidderCatalog bidderCatalog) {

        return new BidderAliases(aliasToBidder, aliasToVendorId, bidderCatalog);
    }

    public boolean isAliasDefined(String alias) {
        return bidderCatalog.isValidName(alias) || aliasToBidder.containsKey(alias);
    }

    public String resolveBidder(String aliasOrBidder) {
        if (bidderCatalog.isValidName(aliasOrBidder)) {
            return aliasOrBidder;
        }

        return aliasToBidder.getOrDefault(aliasOrBidder, aliasOrBidder);
    }

    public boolean isSame(String bidder1, String bidder2) {
        return StringUtils.equalsIgnoreCase(resolveBidder(bidder1), resolveBidder(bidder2));
    }

    public Integer resolveAliasVendorId(String alias) {
        final Integer vendorId = resolveAliasVendorIdViaCatalog(alias);
        return vendorId == null ? aliasToVendorId.get(alias) : vendorId;
    }

    private Integer resolveAliasVendorIdViaCatalog(String alias) {
        final String bidderName = resolveBidder(alias);
        return bidderCatalog.isActive(bidderName) ? bidderCatalog.vendorIdByName(bidderName) : null;
    }
}
