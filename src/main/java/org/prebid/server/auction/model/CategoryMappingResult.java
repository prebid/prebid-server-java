package org.prebid.server.auction.model;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class CategoryMappingResult {

    Map<String, Map<String, String>> biddersToBidsCategories;

    Map<String, Map<String, Boolean>> biddersToBidsSatisfiedPriority;

    List<BidderResponse> bidderResponses;

    List<String> errors;
}
