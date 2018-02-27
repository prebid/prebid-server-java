package org.prebid.server.auction;

import org.prebid.server.bidder.BidderRequester;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides simple access to all {@link BidderRequester}s registered so far.
 */
public class BidderRequesterCatalog {

    private final Map<String, BidderRequester> bidderRequesters;

    public BidderRequesterCatalog(List<BidderRequester> bidderRequesters) {
        this.bidderRequesters = Objects.requireNonNull(bidderRequesters).stream()
                .collect(Collectors.toMap(BidderRequester::name, Function.identity()));
    }

    /**
     * Returns a {@link BidderRequester} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public BidderRequester byName(String name) {
        return bidderRequesters.get(name);
    }

    /**
     * Tells if given name corresponds to any of the registered bidderRequesters.
     */
    public boolean isValidName(String name) {
        return bidderRequesters.containsKey(name);
    }

    /**
     * Returns a list of registered bidder names.
     */
    public Set<String> names() {
        return new HashSet<>(bidderRequesters.keySet());
    }
}
