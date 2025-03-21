package org.prebid.server.bidder;

import org.apache.commons.collections4.map.CaseInsensitiveMap;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides simple access to all {@link Bidder}s and {@link Usersyncer}s registered so far.
 */
public class BidderCatalog {

    private static final String ERROR_MESSAGE_TEMPLATE_FOR_DEPRECATED =
            "%s has been deprecated and is no longer available. Use %s instead.";

    private final Set<String> biddersNames = new HashSet<>();
    private final Map<String, BidderInstanceDeps> bidderDepsMap = new CaseInsensitiveMap<>();
    private final Map<String, String> deprecatedNameToError = new CaseInsensitiveMap<>();
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

        biddersNames.add(bidderName);
        bidderDepsMap.put(bidderName, deps);
        deprecatedNameToError.putAll(createErrorsForDeprecatedNames(deps));
        processVendorId(deps, bidderName);
    }

    private void validateBidderName(String bidderName) {
        if (bidderDepsMap.containsKey(bidderName)) {
            throw new IllegalArgumentException(
                    "Duplicate bidder or alias '%s'. Please check the configuration".formatted(bidderName));
        }
    }

    private Map<String, String> createErrorsForDeprecatedNames(BidderInstanceDeps deps) {
        return deps.getDeprecatedNames().stream().collect(Collectors.toMap(
                Function.identity(),
                deprecatedName -> ERROR_MESSAGE_TEMPLATE_FOR_DEPRECATED.formatted(deprecatedName, deps.getName())));
    }

    private void processVendorId(BidderInstanceDeps coreDeps, String bidderName) {
        Optional.ofNullable(coreDeps.getBidderInfo())
                .map(BidderInfo::getGdpr)
                .map(BidderInfo.GdprInfo::getVendorId)
                .filter(vendorId -> vendorId != 0)
                .ifPresent(vendorId -> vendorIdToBidderName.put(vendorId, bidderName));
    }

    /**
     * Returns a list of registered bidder names.
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(biddersNames);
    }

    /**
     * Tells if given name corresponds to any of the registered bidders.
     */
    public boolean isValidName(String name) {
        return Optional.ofNullable(name)
                .map(bidderDepsMap::containsKey)
                .orElse(false);
    }

    /**
     * Tells if given bidder allows to modify video's Vast XML.
     */
    public boolean isModifyingVastXmlAllowed(String name) {
        return Optional.ofNullable(name)
                .map(bidderDepsMap::get)
                .map(BidderInstanceDeps::getBidderInfo)
                .map(BidderInfo::isModifyingVastXmlAllowed)
                .orElse(false);
    }

    /**
     * Tells if given name corresponds to any of the registered deprecated bidder's name.
     */
    public boolean isDeprecatedName(String name) {
        return Optional.ofNullable(name)
                .map(deprecatedNameToError::containsKey)
                .orElse(false);
    }

    /**
     * Returns associated error for given bidder's name.
     */
    public String errorForDeprecatedName(String name) {
        return Optional.ofNullable(name)
                .map(deprecatedNameToError::get)
                .orElse(null);
    }

    /**
     * Tells if given bidder is enabled and ready for auction.
     */
    public boolean isActive(String name) {
        return Optional.ofNullable(name)
                .map(bidderDepsMap::get)
                .map(BidderInstanceDeps::getBidderInfo)
                .map(BidderInfo::isEnabled)
                .orElse(false);
    }

    /**
     * Tells if given bidder allows debug.
     */
    public boolean isDebugAllowed(String name) {
        return Optional.ofNullable(name)
                .map(bidderDepsMap::get)
                .map(BidderInstanceDeps::getBidderInfo)
                .map(BidderInfo::isDebugAllowed)
                .orElse(false);
    }

    /**
     * Returns an {@link BidderInfo} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public BidderInfo bidderInfoByName(String name) {
        return Optional.ofNullable(name)
                .map(bidderDepsMap::get)
                .map(BidderInstanceDeps::getBidderInfo)
                .orElse(null);
    }

    /**
     * Returns an VendorId registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Integer vendorIdByName(String name) {
        return Optional.ofNullable(name)
                .map(bidderDepsMap::get)
                .map(BidderInstanceDeps::getBidderInfo)
                .map(BidderInfo::getGdpr)
                .map(BidderInfo.GdprInfo::getVendorId)
                .orElse(null);
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

    public Optional<Usersyncer> usersyncerByName(String name) {
        return Optional.ofNullable(name)
                .map(bidderDepsMap::get)
                .map(BidderInstanceDeps::getUsersyncer);
    }

    private Optional<String> aliasOf(String bidder) {
        return Optional.ofNullable(bidder)
                .map(bidderDepsMap::get)
                .map(BidderInstanceDeps::getBidderInfo)
                .map(BidderInfo::getAliasOf);
    }

    public boolean isAlias(String bidder) {
        return aliasOf(bidder).isPresent();
    }

    public String resolveBaseBidder(String bidder) {
        return aliasOf(bidder).orElse(bidder);
    }

    public Optional<String> cookieFamilyName(String bidder) {
        return usersyncerByName(bidder)
                .map(Usersyncer::getCookieFamilyName);
    }

    public Set<String> usersyncReadyBidders() {
        return names().stream()
                .filter(this::isActive)
                .filter(bidder -> usersyncerByName(bidder).isPresent())
                .collect(Collectors.toSet());
    }

    /**
     * Returns an {@link Bidder} registered by the given name or null if there is none.
     * <p>
     * Therefore this method should be called only for names that previously passed validity check
     * through calling {@link #isValidName(String)}.
     */
    public Bidder<?> bidderByName(String name) {
        return Optional.ofNullable(name)
                .map(bidderDepsMap::get)
                .map(BidderInstanceDeps::getBidder)
                .orElse(null);
    }
}
