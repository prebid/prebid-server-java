package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.util.StreamUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

@AllArgsConstructor(staticName = "of")
public class BidderEnrichmentSampler {

    public static final String PREBID_BIDDER_PATH = "/prebid/bidder";
    public static final String BIDDER_PATH = "/bidder";
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
        final Set<ObjectNode> impExt = Optional.ofNullable(bidRequest.getImp())
                .stream()
                .flatMap(Collection::stream)
                .map(Imp::getExt)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final Set<String> bidders = extractBiddersByPath(impExt, PREBID_BIDDER_PATH);
        return CollectionUtils.isNotEmpty(bidders)
                ? bidders
                : extractBiddersByPath(impExt, BIDDER_PATH);

    }

    private static Set<String> extractBiddersByPath(Set<ObjectNode> impExt, String path) {
        if (CollectionUtils.isEmpty(impExt)) {
            return Collections.emptySet();
        }

        return impExt.stream()
                .map(ext -> ext.at(path))
                .filter(Objects::nonNull)
                .flatMap(bidder -> StreamUtil.asStream(bidder.fieldNames()))
                .collect(Collectors.toSet());
    }
}
