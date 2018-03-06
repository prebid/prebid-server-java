package org.prebid.server.bidder;

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

    public BidderCatalog(List<BidderDeps> bidderDeps) {
        bidderDepsMap = Objects.requireNonNull(bidderDeps).stream()
                .collect(Collectors.toMap(BidderDeps::getName, Function.identity()));
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
     * Returns an {@link Adapter} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Adapter adapterByName(String name) {
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
