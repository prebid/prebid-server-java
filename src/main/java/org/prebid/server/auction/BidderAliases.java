package org.prebid.server.auction;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.proto.openrtb.ext.request.AlternateBidder;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodes;
import org.prebid.server.settings.model.AccountAlternateBidderCodes;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents aliases configured for bidders - configuration might come in OpenRTB request but not limited to it.
 */
public class BidderAliases {

    private static final String WILDCARD = "*";

    private final Map<String, String> aliasToBidder;

    private final Map<String, Integer> aliasToVendorId;

    private final Map<String, Set<String>> bidderToAllowedBidderCodes;

    private final BidderCatalog bidderCatalog;

    private BidderAliases(Map<String, String> aliasToBidder,
                          Map<String, Integer> aliasToVendorId,
                          Map<String, Set<String>> bidderToAllowedBidderCodes,
                          BidderCatalog bidderCatalog) {

        this.aliasToBidder = new CaseInsensitiveMap<>(MapUtils.emptyIfNull(aliasToBidder));
        this.aliasToVendorId = new CaseInsensitiveMap<>(MapUtils.emptyIfNull(aliasToVendorId));
        this.bidderToAllowedBidderCodes = MapUtils.emptyIfNull(bidderToAllowedBidderCodes);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    public static BidderAliases of(Map<String, String> aliasToBidder,
                                   Map<String, Integer> aliasToVendorId,
                                   BidderCatalog bidderCatalog) {

        return new BidderAliases(aliasToBidder, aliasToVendorId, null, bidderCatalog);
    }

    public static BidderAliases of(Map<String, String> aliasToBidder,
                                   Map<String, Integer> aliasToVendorId,
                                   BidderCatalog bidderCatalog,
                                   AccountAlternateBidderCodes alternateBidderCodes) {

        final Map<String, Set<String>> bidderToAllowedSeats = resolveAlternateBidderCodes(
                        alternateBidderCodes,
                        AccountAlternateBidderCodes::getEnabled,
                        AccountAlternateBidderCodes::getBidders);

        return new BidderAliases(aliasToBidder, aliasToVendorId, bidderToAllowedSeats, bidderCatalog);
    }

    public static BidderAliases of(Map<String, String> aliasToBidder,
                                   Map<String, Integer> aliasToVendorId,
                                   BidderCatalog bidderCatalog,
                                   ExtRequestPrebidAlternateBidderCodes alternateBidderCodes) {

        final Map<String, Set<String>> bidderToAllowedSeats = resolveAlternateBidderCodes(
                alternateBidderCodes,
                ExtRequestPrebidAlternateBidderCodes::getEnabled,
                ExtRequestPrebidAlternateBidderCodes::getBidders);

        return new BidderAliases(aliasToBidder, aliasToVendorId, bidderToAllowedSeats, bidderCatalog);
    }

    public boolean isAliasDefined(String alias) {
        return aliasToBidder.containsKey(alias);
    }

    public String resolveBidder(String aliasOrBidder) {
        return bidderCatalog.isValidName(aliasOrBidder)
                ? aliasOrBidder
                : aliasToBidder.getOrDefault(aliasOrBidder, aliasOrBidder);
    }

    public boolean isSame(String bidder1, String bidder2) {
        return StringUtils.equalsIgnoreCase(resolveBidder(bidder1), resolveBidder(bidder2));
    }

    public Integer resolveAliasVendorId(String alias) {
        final Integer vendorId = resolveAliasVendorIdViaCatalog(alias);
        return vendorId == null ? aliasToVendorId.get(alias) : vendorId;
    }

    private Integer resolveAliasVendorIdViaCatalog(String alias) {
        final String bidderName = resolveBidder(alias);
        return bidderCatalog.isActive(bidderName) ? bidderCatalog.vendorIdByName(bidderName) : null;
    }

    public boolean isAllowedAlternateBidderCode(String bidder, String alternateBidderCode) {
        final Set<String> allowedBidderCodes = bidderToAllowedBidderCodes.getOrDefault(bidder, Collections.emptySet());
        return allowedBidderCodes.contains(WILDCARD) || allowedBidderCodes.contains(alternateBidderCode);
    }

    private static <T> Map<String, Set<String>> resolveAlternateBidderCodes(
            T alternateBidderCodes,
            Function<T, Boolean> isEnabled,
            Function<T, Map<String, ? extends AlternateBidder>> getBidders) {

        return Optional.ofNullable(alternateBidderCodes)
                .filter(codes -> BooleanUtils.isTrue(isEnabled.apply(codes)))
                .map(getBidders::apply)
                .map(Map::entrySet)
                .stream()
                .flatMap(Collection::stream)
                .filter(entry -> BooleanUtils.isTrue(entry.getValue().getEnabled()))
                .map(entry -> Map.entry(entry.getKey(), allowedBidderCodes(entry.getValue())))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (first, second) -> second,
                        CaseInsensitiveMap::new));
    }

    private static Set<String> allowedBidderCodes(AlternateBidder alternateBidder) {
        final Set<String> allowedBidderCodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Optional.ofNullable(alternateBidder.getAllowedBidderCodes())
                .ifPresentOrElse(allowedBidderCodes::addAll, () -> allowedBidderCodes.add(WILDCARD));
        return allowedBidderCodes;
    }
}
