package org.prebid.server.functional.model.config

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.util.PBSUtils

class RuleEngineModelRuleResultsArgs {

    List<BidderName> bidders
    Integer seatNonBid
    String analyticsValue
    Boolean ifSyncedId

    static RuleEngineModelRuleResultsArgs createRuleEngineModelRuleResultsArgs(BidderName bidderName){
        new RuleEngineModelRuleResultsArgs().tap {
            it.bidders = [bidderName]
            it.analyticsValue = PBSUtils.randomString
            it.seatNonBid = 201
        }
    }

    static RuleEngineModelRuleResultsArgs createRuleEngineModelRuleResultsArgsOnlyATag(){
        new RuleEngineModelRuleResultsArgs().tap {
            it.analyticsValue = PBSUtils.randomString
        }
    }
}
