package org.prebid.server.bidder;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides simple access to all {@link Bidder}s and {@link Usersyncer}s registered so far.
 */
public class BidderCatalog {

    private static final String ERROR_MESSAGE_TEMPLATE_FOR_DEPRECATED =
            "%s has been deprecated and is no longer available. Use %s instead.";

    private final Map<String, BidderInstanceDeps> bidderDepsMap = new HashMap<>();
    private final Map<String, String> deprecatedNameToError = new HashMap<>();
    private final Map<Integer, String> vendorIdToBidderName = new HashMap<>();

    public BidderCatalog(List<BidderDeps> bidderDeps) {
        Objects.requireNonNull(bidderDeps).stream()
                .map(BidderDeps::getInstances)
                .flatMap(Collection::stream)
                .forEach(this::processDeps);
    }

    private void processDeps(BidderInstanceDeps deps) {
        final String bidderName = deps.getName();

        validateBidderName(bidderName);

        bidderDepsMap.put(bidderName, deps);
        deprecatedNameToError.putAll(createErrorsForDeprecatedNames(deps));
        processVendorId(deps, bidderName);
    }

    private void validateBidderName(String bidderName) {
        if (bidderDepsMap.containsKey(bidderName)) {
            throw new IllegalArgumentException(String.format(
                    "Duplicate bidder or alias '%s'. Please check the configuration", bidderName));
        }
    }

    private Map<String, String> createErrorsForDeprecatedNames(BidderInstanceDeps deps) {
        return deps.getDeprecatedNames().stream().collect(Collectors.toMap(
                Function.identity(),
                deprecatedName -> String.format(
                        ERROR_MESSAGE_TEMPLATE_FOR_DEPRECATED, deprecatedName, deps.getName())));
    }

    private void processVendorId(BidderInstanceDeps coreDeps, String bidderName) {
        final BidderInfo bidderInfo = coreDeps.getBidderInfo();
        final BidderInfo.GdprInfo gdprInfo = bidderInfo != null ? bidderInfo.getGdpr() : null;
        final Integer vendorId = gdprInfo != null ? gdprInfo.getVendorId() : null;

        if (vendorId != null && vendorId != 0) {
            vendorIdToBidderName.put(vendorId, bidderName);
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

    /**
     * Tells if given bidder allows to modify video's Vast XML.
     */
    public boolean isModifyingVastXmlAllowed(String name) {
        return bidderDepsMap.containsKey(name) && bidderDepsMap.get(name).getBidderInfo().isModifyingVastXmlAllowed();
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
     * Tells if given bidder is enabled and ready for auction.
     */
    public boolean isActive(String name) {
        return bidderDepsMap.containsKey(name) && bidderDepsMap.get(name).getBidderInfo().isEnabled();
    }

    /**
     * Tells if given bidder allows debug.
     */
    public boolean isDebugAllowed(String name) {
        return bidderDepsMap.containsKey(name) && bidderDepsMap.get(name).getBidderInfo().isDebugAllowed();
    }

    /**
     * Returns an {@link BidderInfo} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public BidderInfo bidderInfoByName(String name) {
        final BidderInstanceDeps bidderDeps = bidderDepsMap.get(name);
        return bidderDeps != null ? bidderDeps.getBidderInfo() : null;
    }

    /**
     * Returns an VendorId registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Integer vendorIdByName(String name) {
        final BidderInstanceDeps bidderDeps = bidderDepsMap.get(name);
        final BidderInfo bidderInfo = bidderDeps != null ? bidderDeps.getBidderInfo() : null;
        final BidderInfo.GdprInfo gdprInfo = bidderInfo != null ? bidderInfo.getGdpr() : null;
        return gdprInfo != null ? gdprInfo.getVendorId() : null;
    }

    /**
     * Returns a Bidder name registered by the vendor ID or null if there is none.
     */
    public String nameByVendorId(Integer vendorId) {
        return vendorIdToBidderName.get(vendorId);
    }

    /**
     * Returns VendorIds configured in configuration for prebid server.
     */
    public Set<Integer> knownVendorIds() {
        return bidderDepsMap.values().stream()
                .map(BidderInstanceDeps::getBidderInfo)
                .map(BidderInfo::getGdpr)
                .map(BidderInfo.GdprInfo::getVendorId)
                // TODO change to notNull when migrate from primitives to Object
                // .filter(id -> id != 0)
                .collect(Collectors.toSet());
    }

    /**
     * Returns an {@link Usersyncer} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Usersyncer usersyncerByName(String name) {
        final BidderInstanceDeps bidderDeps = bidderDepsMap.get(name);
        return bidderDeps != null ? bidderDeps.getUsersyncer() : null;
    }

    /**
     * Returns an {@link Bidder} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Bidder<?> bidderByName(String name) {
        final BidderInstanceDeps bidderDeps = bidderDepsMap.get(name);
        return bidderDeps != null ? bidderDeps.getBidder() : null;
    }
}
