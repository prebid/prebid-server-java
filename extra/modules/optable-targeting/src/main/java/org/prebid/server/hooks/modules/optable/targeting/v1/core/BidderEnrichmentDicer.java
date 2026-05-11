package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import lombok.AllArgsConstructor;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.util.StreamUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@AllArgsConstructor(staticName = "of")
public class BidderEnrichmentDicer {

    private final AliasesResolver aliasesResolver;

    public Set<String> dice(BidRequest bidRequest, OptableTargetingProperties optableTargetingProperties) {
        final Integer defaultEnrichmentPercentage = optableTargetingProperties.getEnrichmentPercentage();
        final Map<String, Integer> bidderEnrichmentPercentage =
                optableTargetingProperties.getBidderEnrichmentPercentages();

        final BidderAliases aliases = aliasesResolver.resolve(bidRequest);
        final Set<String> bidders = extractUniqueBidders(bidRequest)
                .stream()
                .filter(bidder -> {
                    final int percentage =
                            resolvePercentage(aliases, bidder, defaultEnrichmentPercentage, bidderEnrichmentPercentage);
                    return ThreadLocalRandom.current().nextInt(100) <= percentage;
                })
                .collect(Collectors.toSet());

        return bidders;
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
                .map(ext -> ext.at("/prebid/bidder"))
                .filter(Objects::nonNull)
                .flatMap(bidder -> StreamUtil.asStream(bidder.fieldNames()))
                .collect(Collectors.toSet());
    }
}
