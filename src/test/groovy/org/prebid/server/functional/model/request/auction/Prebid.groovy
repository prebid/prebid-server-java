package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class Prebid {

    Integer debug
    Targeting targeting
    PrebidCache cache
    PrebidStoredRequest storedRequest
    Amp amp
    Channel channel
    Map<String, String> aliases
    List<PrebidSchain> schains
    List<MultiBid> multibid
    Pbs pbs
    Map<BidderName, Map<String, Integer>> bidderParams
}
