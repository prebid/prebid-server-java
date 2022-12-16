package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class OpenxBidResponseExt extends BidResponseExt {

    @JsonProperty("fledge_auction_configs")
    Map<String, Map> fledgeAuctionConfigs
}
