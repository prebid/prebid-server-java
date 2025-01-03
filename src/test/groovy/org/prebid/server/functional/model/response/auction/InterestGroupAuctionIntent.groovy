package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class InterestGroupAuctionIntent {

    String impId
    @JsonProperty("igb")
    List<InterestGroupAuctionBuyer> interestGroupAuctionBuyer
    @JsonProperty("igs")
    List<InterestGroupAuctionSeller> interestGroupAuctionSeller
    InterestGroupAuctionIntentExt ext
}
