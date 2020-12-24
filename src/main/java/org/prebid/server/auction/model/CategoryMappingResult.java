package org.prebid.server.auction.model;

import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class CategoryMappingResult {

    Map<String, Map<String, String>> biddersToBidsCategories;

    List<BidderResponse> bidderResponses;

    List<String> errors;
}
