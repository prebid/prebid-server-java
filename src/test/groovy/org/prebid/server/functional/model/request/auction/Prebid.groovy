package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@JsonNaming(PropertyNamingStrategy.LowerCaseStrategy.class)
@ToString(includeNames = true, ignoreNulls = true)
class Prebid {

    Integer debug
    Targeting targeting
    PrebidCache cache
    StoredRequest storedRequest
    Amp amp
    Channel channel
    List<PrebidSchain> schains
    List<MultiBid> multibid
    Pbs pbs
    Map<BidderName, Map<String, Integer>> bidderParams
}
