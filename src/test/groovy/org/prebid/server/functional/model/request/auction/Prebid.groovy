package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.bidder.BidderName

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class Prebid {

    Integer debug
    Boolean returnAllBidStatus
    Map<String, BidderName> aliases
    Map<String, Integer> aliasgvlids
    BidAdjustmentFactors bidAdjustmentFactors
    PrebidCurrency currency
    Targeting targeting
    TraceLevel trace
    PrebidStoredRequest storedRequest
    PrebidCache cache
    ExtRequestPrebidData data
    List<ExtPrebidBidderConfig> bidderConfig
    List<PrebidSchain> schains
    Amp amp
    Channel channel
    List<MultiBid> multibid
    Pbs pbs
    Server server
    Map<BidderName, Map<String, Integer>> bidderParams
    ExtPrebidFloors floors
    Map passThrough
    Events events
    Boolean createTids
    Sdk sdk
    List<AdServerTargeting> adServerTargeting
    BidderControls bidderControls
    PrebidModulesConfig modules

    static class Channel {

        ChannelType name
        String version
    }
}
