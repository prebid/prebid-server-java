package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import lombok.AllArgsConstructor;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.requestfactory.Ortb2ImplicitParametersResolver;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.util.StreamUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor(staticName = "of")
public class BidderEnrichmentSampler {

    private static final String PREBID_BIDDER_PATH = "/prebid/bidder";

    private final AliasesResolver aliasesResolver;
    private final IntSupplier randomSupplier;

    public static BidderEnrichmentSampler of(AliasesResolver aliasesResolver) {
        return of(aliasesResolver, () -> ThreadLocalRandom.current().nextInt(100));
    }

    public Set<String> sample(BidRequest bidRequest, OptableTargetingProperties optableTargetingProperties) {
        final Integer defaultEnrichmentPercentage = optableTargetingProperties.getEnrichmentPercentage();
        final Map<String, Integer> bidderEnrichmentPercentage =
                optableTargetingProperties.getBidderEnrichmentPercentages();

        final BidderAliases aliases = aliasesResolver.resolve(bidRequest);
        return extractUniqueBidders(bidRequest)
                .stream()
                .filter(bidder -> {
                    final int percentage =
                            resolvePercentage(aliases, bidder, defaultEnrichmentPercentage, bidderEnrichmentPercentage);
                    return percentage > 0 && randomSupplier.getAsInt() < percentage;
                })
                .collect(Collectors.toSet());
    }

    private static int resolvePercentage(BidderAliases aliases, String bidder,
                                         Integer defaultEnrichmentPercentage,
                                         Map<String, Integer> bidderEnrichmentPercentage) {

        return Optional.ofNullable(bidderEnrichmentPercentage.get(bidder))
                .or(() -> Optional.ofNullable(bidderEnrichmentPercentage.get(aliases.resolveBidder(bidder))))
                .orElse(defaultEnrichmentPercentage);
    }

    private static Set<String> extractUniqueBidders(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getImp())
                .stream()
                .flatMap(Collection::stream)
                .map(Imp::getExt)
                .filter(Objects::nonNull)
                .flatMap(BidderEnrichmentSampler::extractImpBidders)
                .collect(Collectors.toSet());
    }

    /**
     * Follows the core, which at a later stage moves old-style imp.ext.BIDDER params into imp.ext.prebid.bidder
     * for the imps that have no bidders there yet.
     */
    private static Stream<String> extractImpBidders(ObjectNode impExt) {
        final JsonNode prebidBidders = impExt.at(PREBID_BIDDER_PATH);
        if (prebidBidders.isObject() && !prebidBidders.isEmpty()) {
            return StreamUtil.asStream(prebidBidders.fieldNames());
        }

        return StreamUtil.asStream(impExt.fieldNames())
                .filter(Ortb2ImplicitParametersResolver::isImpExtBidder)
                .filter(field -> impExt.get(field).isObject());
    }
}
