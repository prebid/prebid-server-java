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

    private static final String ERROR_MESSAGE_TEMPLATE_FOR_DEPRECATED = "%s has been deprecated and is no "
            + "longer available. Use %s instead.";

    private final Map<String, BidderDeps> bidderDepsMap;
    private final Map<String, String> deprecatedNameToError = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public BidderCatalog(List<BidderDeps> bidderDeps) {
        bidderDepsMap = Objects.requireNonNull(bidderDeps).stream()
                .collect(Collectors.toMap(BidderDeps::getName, Function.identity()));

        for (BidderDeps deps : bidderDeps) {
            deprecatedNameToError.putAll(createErrorsForDeprecatedNames(deps.getDeprecatedNames(), deps.getName()));
            aliases.putAll(createAliases(deps.getAliases(), deps.getName()));
        }
    }

    private Map<String, String> createErrorsForDeprecatedNames(List<String> deprecatedNames, String name) {
        return deprecatedNames.stream().collect(Collectors.toMap(Function.identity(),
                deprecatedName -> String.format(ERROR_MESSAGE_TEMPLATE_FOR_DEPRECATED, deprecatedName, name)));
    }

    private Map<String, String> createAliases(List<String> aliases, String name) {
        return aliases.stream().collect(Collectors.toMap(Function.identity(), ignored -> name));
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
     * Tells if given name corresponds to any of the registered deprecated bidder's name.
     */
    public boolean isDeprecatedName(String name) {
        return deprecatedNameToError.containsKey(name);
    }

    /**
     * Returns associated error for given bidder's name.
     */
    public String errorForDeprecatedName(String name) {
        return deprecatedNameToError.get(name);
    }

    /**
     * Tells if given name corresponds to any of the registered bidder's alias.
     */
    public boolean isAlias(String name) {
        return aliases.containsKey(name);
    }

    /**
     * Returns original bidder's name for given alias.
     */
    public String nameByAlias(String alias) {
        return aliases.get(alias);
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
