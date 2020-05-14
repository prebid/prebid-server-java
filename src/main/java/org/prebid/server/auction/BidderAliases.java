package org.prebid.server.auction;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Collections;
import java.util.Map;

/**
 * Represents aliases configured for bidders - configuration might come in OpenRTB request but not limited to it.
 */
public class BidderAliases {

    private Map<String, String> aliasToBidder;

    private Map<String, Integer> aliasToVendorId;

    private BidderAliases(Map<String, String> aliasToBidder, Map<String, Integer> aliasToVendorId) {
        this.aliasToBidder = ObjectUtils.firstNonNull(aliasToBidder, Collections.emptyMap());
        this.aliasToVendorId = ObjectUtils.firstNonNull(aliasToVendorId, Collections.emptyMap());
    }

    public static BidderAliases of(Map<String, String> aliasToBidder, Map<String, Integer> aliasToVendorId) {
        return new BidderAliases(aliasToBidder, aliasToVendorId);
    }

    public boolean isAliasDefined(String alias) {
        return aliasToBidder.containsKey(alias);
    }

    public String resolveBidder(String aliasOrBidder) {
        return aliasToBidder.getOrDefault(aliasOrBidder, aliasOrBidder);
    }

    public Integer resolveAliasVendorId(String alias) {
        return aliasToVendorId.get(alias);
    }
}
