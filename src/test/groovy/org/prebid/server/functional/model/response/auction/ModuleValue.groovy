package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName
import org.prebid.server.functional.model.config.ResultFunction
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class ModuleValue {

   ModuleName module
   String richmediaFormat
    @JsonProperty("analytics_key")
   String analyticsKey
    @JsonProperty("analytics_value")
   String analyticsValue
    @JsonProperty("model_version")
   String modelVersion
    @JsonProperty("condition_fired")
   List<String> conditionFired
    @JsonProperty("rule_fired")
    String rule_fired
    @JsonProperty("result_functions")
   List<ResultFunction> resultFunctions
    @JsonProperty("bidders_removed")
   List<BidderName> biddersRemoved
   @JsonProperty("seatnonbid")
   String seatNonBid
   String message
}
