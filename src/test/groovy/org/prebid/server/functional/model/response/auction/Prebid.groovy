package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class Prebid {

    MediaType type
    Map<String, String> targeting
    String targetBidderCode
    Cache cache
    Map passThrough
}
