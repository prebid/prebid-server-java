package org.prebid.server.bidder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides simple access to all {@link Adapter}s, {@link BidderRequester}s and {@link Usersyncer}s registered so far.
 */
public class BidderCatalog {

    private final Map<String, BidderDeps> bidderDepsMap;

    private final Map<String, BidderDeps> deprecatedBidderDepsMap = new HashMap<>();

    public BidderCatalog(List<BidderDeps> bidderDeps) {
        bidderDepsMap = Objects.requireNonNull(bidderDeps).stream()
                .collect(Collectors.toMap(BidderDeps::getName, Function.identity()));

        for (BidderDeps bidderDepsInner : bidderDeps) {
            for (String deprecatedBidderName : bidderDepsInner.getDeprecatedNames()) {
                deprecatedBidderDepsMap.put(deprecatedBidderName, bidderDepsInner);
            }
        }
    }

    /**
     * Returns a list of registered bidder names.
     */
    public Set<String> names() {
        return new HashSet<>(bidderDepsMap.keySet());
    }

    /**
     * Tells if given name corresponds to any of the registered bidders.
     */
    public boolean isValidName(String name) {
        return bidderDepsMap.containsKey(name);
    }

    public boolean isDeprecatedName(String name) {
        return deprecatedBidderDepsMap.containsKey(name);
    }

    public String getNewNameForDeprecatedBidder(String name) {
        return deprecatedBidderDepsMap.get(name).getName();
    }

    /**
     * Tells if given bidder is enabled and ready for auction.
     */
    public boolean isActive(String name) {
        return bidderDepsMap.containsKey(name) && bidderDepsMap.get(name).getMetaInfo().info().isEnabled();
    }

    /**
     * Tells if adapter with given name exists.
     */
    public boolean isValidAdapterName(String name) {
        return bidderDepsMap.containsKey(name) && adapterByName(name) != null;
    }

    /**
     * Returns an {@link MetaInfo} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public MetaInfo metaInfoByName(String name) {
        final BidderDeps bidderDeps = bidderDepsMap.get(name);
        return bidderDeps != null ? bidderDeps.getMetaInfo() : null;
    }

    /**
     * Returns an {@link Usersyncer} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Usersyncer usersyncerByName(String name) {
        final BidderDeps bidderDeps = bidderDepsMap.get(name);
        return bidderDeps != null ? bidderDeps.getUsersyncer() : null;
    }

    /**
     * Returns an {@link Bidder} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Bidder<?> bidderByName(String name) {
        final BidderDeps bidderDeps = bidderDepsMap.get(name);
        return bidderDeps != null ? bidderDeps.getBidder() : null;
    }

    /**
     * Returns an {@link Adapter} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Adapter<?, ?> adapterByName(String name) {
        final BidderDeps bidderDeps = bidderDepsMap.get(name);
        return bidderDeps != null ? bidderDeps.getAdapter() : null;
    }

    /**
     * Returns a {@link BidderRequester} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public BidderRequester bidderRequesterByName(String name) {
        final BidderDeps bidderDeps = bidderDepsMap.get(name);
        return bidderDeps != null ? bidderDeps.getBidderRequester() : null;
    }
}
