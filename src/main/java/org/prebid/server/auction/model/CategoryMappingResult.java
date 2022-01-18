package org.prebid.server.auction.model;

import com.iab.openrtb.response.Bid;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class CategoryMappingResult {

    Map<Bid, String> biddersToBidsCategories;

    Map<Bid, Boolean> biddersToBidsSatisfiedPriority;

    List<BidderResponse> bidderResponses;

    PrebidLog prebidLog;

    public static CategoryMappingResult of(List<BidderResponse> bidderResponses) {
        return CategoryMappingResult.of(
                Collections.emptyMap(),
                Collections.emptyMap(),
                bidderResponses,
                PrebidLog.empty());
    }

    public String getCategory(Bid bid) {
        return biddersToBidsCategories.get(bid);
    }

    public Boolean isBidSatisfiesPriority(Bid bid) {
        return biddersToBidsSatisfiedPriority.get(bid);
    }
}
