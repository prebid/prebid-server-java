package org.prebid.server.auction;

import org.prebid.server.bidder.Bidder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides simple access to all bidders registered so far.
 */
public class BidderCatalog {

    private final Map<String, Bidder> bidders;

    public BidderCatalog(List<Bidder> bidderList) {
        bidders = bidderList.stream().collect(Collectors.toMap(Bidder::name, Function.identity()));

    }

    /**
     * Returns a bidder registered by the given name or null if there is none. Therefore this method should be called
     * only for names that previously passed validity check through calling {@link #isValidName(String)}.
     */
    Bidder byName(String name) {
        return bidders.get(name);
    }

    /**
     * Tells if given name corresponds to any of the registered bidders.
     */
    public boolean isValidName(String name) {
        return bidders.containsKey(name);
    }

    /**
     * Returns a list of registered bidder names.
     */
    public Set<String> names() {
        return new HashSet<>(bidders.keySet());
    }
}
