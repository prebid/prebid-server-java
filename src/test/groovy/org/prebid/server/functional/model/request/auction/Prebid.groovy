package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AlternateBidderCodes

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class Prebid {

    DebugCondition debug
    Boolean returnAllBidStatus
    Map<String, BidderName> aliases
    Map<String, Integer> aliasgvlids
    BidAdjustmentFactors bidAdjustmentFactors
    BidAdjustment bidAdjustments
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
    Map bidderParams
    ExtPrebidFloors floors
    Map passThrough
    Events events
    Boolean createTids
    Sdk sdk
    List<AdServerTargeting> adServerTargeting
    BidderControls bidderControls
    PrebidModulesConfig modules
    PrebidAnalytics analytics
    StoredAuctionResponse storedAuctionResponse
    PaaFormat paaFormat
    @JsonProperty("alternatebiddercodes")
    AlternateBidderCodes alternateBidderCodes
    @JsonProperty("profiles")
    List<String> profilesNames

    static class Channel {

        ChannelType name
        String version
    }
}
