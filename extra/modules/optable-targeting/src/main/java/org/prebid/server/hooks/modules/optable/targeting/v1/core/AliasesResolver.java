package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.openrtb.request.BidRequest;
import lombok.AllArgsConstructor;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.util.PbsUtil;

import java.util.Map;
import java.util.Optional;

@AllArgsConstructor(staticName = "of")
public class AliasesResolver {

    private final BidderCatalog bidderCatalog;

    public BidderAliases resolve(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(PbsUtil::extRequestPrebid)
                .map(extRequestPrebid -> {
                    final Map<String, String> aliases = extRequestPrebid.getAliases();
                    final Map<String, Integer> aliasesGvlIds = extRequestPrebid.getAliasgvlids();
                    return BidderAliases.of(aliases, aliasesGvlIds, bidderCatalog);
                })
                .orElseGet(() -> BidderAliases.of(Map.of(), Map.of(), bidderCatalog));
    }
}
