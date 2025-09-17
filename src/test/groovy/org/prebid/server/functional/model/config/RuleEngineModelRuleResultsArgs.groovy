package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.response.auction.BidRejectionReason
import org.prebid.server.functional.util.PBSUtils

class RuleEngineModelRuleResultsArgs {

    List<BidderName> bidders
    @JsonProperty("seatnonbid")
    BidRejectionReason seatNonBid
    @JsonProperty("analyticsValue")
    String analyticsValue
    @JsonProperty("ifSyncedId")
    Boolean ifSyncedId

    static RuleEngineModelRuleResultsArgs createRuleEngineModelRuleResultsArgs(BidderName bidderName){
        new RuleEngineModelRuleResultsArgs().tap {
            it.bidders = [bidderName]
            it.analyticsValue = PBSUtils.randomString
        }
    }

    static RuleEngineModelRuleResultsArgs createRuleEngineModelRuleResultsArgsOnlyATag(){
        new RuleEngineModelRuleResultsArgs().tap {
            it.analyticsValue = PBSUtils.randomString
        }
    }
}
