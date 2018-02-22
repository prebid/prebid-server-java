package org.prebid.auction;

import org.prebid.bidder.BidderRequester;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides simple access to all httpConnectors.
 */
public class BidderRequesterCatalog {

    private final Map<String, BidderRequester> httpConnectors;

    public BidderRequesterCatalog(List<BidderRequester> bidderRequesters) {
        Objects.requireNonNull(bidderRequesters);
        this.httpConnectors = bidderRequesters.stream().collect(Collectors.toMap(BidderRequester::name,
                Function.identity()));
    }

    /**
     * Returns a {@link BidderRequester} registered by the given name or null if there is none. Therefore this method
     * should be called only for names that previously passed validity check through calling
     * {@link #isValidName(String)}.
     */
    public BidderRequester byName(String name) {
        return httpConnectors.get(name);
    }

    /**
     * Tells if given name corresponds to any of the registered httpConnectors.
     */
    public boolean isValidName(String name) {
        return httpConnectors.containsKey(name);
    }

    /**
     * Returns a list of registered bidder names.
     */
    public Set<String> names() {
        return new HashSet<>(httpConnectors.keySet());
    }
}
