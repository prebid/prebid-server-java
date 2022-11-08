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
    Map<String, BidderName> aliases
    Map<String, Integer> aliasgvlids
    BidAdjustmentFactors bidAdjustmentFactors
    PrebidCurrency currency
    Targeting targeting
    PrebidStoredRequest storedRequest
    PrebidCache cache
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

    static class Channel {

        ChannelType name
        String version
    }
}
