package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class ModuleValue {

    ModuleName module
    @JsonProperty("richmedia-format")
    String richmediaFormat
    String analyticsKey
    String analyticsValue
    String modelVersion
    String conditionFired
    String resultFunction
    List<BidderName> biddersRemoved
    BidRejectionReason seatNonBid
    String message
}
