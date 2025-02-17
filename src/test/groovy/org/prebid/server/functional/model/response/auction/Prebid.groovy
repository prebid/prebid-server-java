package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.prebid.server.functional.model.request.auction.Video

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class Prebid {

    MediaType type
    Map<String, String> targeting
    String targetBidderCode
    Cache cache
    Events events
    Meta meta
    Map passThrough
    Video storedRequestAttributes
}
