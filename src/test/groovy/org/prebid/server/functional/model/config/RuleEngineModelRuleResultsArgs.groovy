package org.prebid.server.functional.model.config

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.response.auction.BidRejectionReason
import org.prebid.server.functional.util.PBSUtils

class RuleEngineModelRuleResultsArgs {

    List<BidderName> bidders
    BidRejectionReason seatNonBid
    String analyticsValue
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
