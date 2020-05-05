package org.prebid.server.auction;

import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.BidderCatalog;

import java.util.Collections;
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

        this.aliasToBidder = ObjectUtils.firstNonNull(aliasToBidder, Collections.emptyMap());
        this.aliasToVendorId = ObjectUtils.firstNonNull(aliasToVendorId, Collections.emptyMap());
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    public static BidderAliases of(
            Map<String, String> aliasToBidder, Map<String, Integer> aliasToVendorId, BidderCatalog bidderCatalog) {

        return new BidderAliases(aliasToBidder, aliasToVendorId, bidderCatalog);
    }

    public boolean isAliasDefined(String alias) {
        return aliasToBidder.containsKey(alias) || bidderCatalog.isAlias(alias);
    }

    public String resolveBidder(String aliasOrBidder) {
        return aliasToBidder.containsKey(aliasOrBidder)
                ? aliasToBidder.get(aliasOrBidder)
                : ObjectUtils.firstNonNull(bidderCatalog.nameByAlias(aliasOrBidder), aliasOrBidder);
    }

    public Integer resolveAliasVendorId(String alias) {
        return aliasToVendorId.get(alias);
    }
}
