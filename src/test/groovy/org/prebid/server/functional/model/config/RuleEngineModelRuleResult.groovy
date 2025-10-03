package org.prebid.server.functional.model.config

import org.prebid.server.functional.model.bidder.BidderName

import static org.prebid.server.functional.model.bidder.BidderName.ACEEX
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.config.ResultFunction.EXCLUDE_BIDDER
import static org.prebid.server.functional.model.config.ResultFunction.INCLUDE_BIDDERS
import static org.prebid.server.functional.model.config.ResultFunction.LOG_A_TAG

class RuleEngineModelRuleResult {

    ResultFunction function
    RuleEngineModelRuleResultsArgs args

    static RuleEngineModelRuleResult createRuleEngineModelRuleWithIncludeResult(BidderName bidderName = ACEEX,
                                                                                Boolean ifSyncedId = false) {
        new RuleEngineModelRuleResult().tap {
            it.function = INCLUDE_BIDDERS
            it.args = RuleEngineModelRuleResultsArgs.createRuleEngineModelRuleResultsArgs(bidderName, ifSyncedId)
        }
    }

    static RuleEngineModelRuleResult createRuleEngineModelRuleWithExcludeResult(BidderName bidderName = OPENX,
                                                                                Boolean ifSyncedId = false) {
        new RuleEngineModelRuleResult().tap {
            it.function = EXCLUDE_BIDDER
            it.args = RuleEngineModelRuleResultsArgs.createRuleEngineModelRuleResultsArgs(bidderName, ifSyncedId)
        }
    }

    static RuleEngineModelRuleResult createRuleEngineModelRuleWithLogATagResult() {
        new RuleEngineModelRuleResult().tap {
            it.function = LOG_A_TAG
            it.args = RuleEngineModelRuleResultsArgs.createRuleEngineModelRuleResultsArgsOnlyATag()
        }
    }
}
