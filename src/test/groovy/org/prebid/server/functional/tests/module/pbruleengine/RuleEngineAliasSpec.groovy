package org.prebid.server.functional.tests.module.pbruleengine

import org.prebid.server.functional.model.ModuleName
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.request.auction.Amx
import org.prebid.server.functional.model.request.auction.Bidder
import org.prebid.server.functional.model.request.auction.Imp

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.OPENX_ALIAS
import static org.prebid.server.functional.model.config.PbRulesEngine.createRulesEngineWithRule
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithExcludeResult
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithIncludeResult
import static org.prebid.server.functional.model.request.auction.FetchStatus.SUCCESS
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

class RuleEngineAliasSpec extends RuleEngineBaseSpec {

    def "PBS should leave only hard alias bidder at imps when hard alias bidder include in account config"() {
        given: "Bid request with multiply imps bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(Imp.defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                updateImpWithOpenXAndAmxAndOpenXAliasBidder(it)
            }
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(OPENX_ALIAS)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == 1

        and: "Bid response should contain seatBid.seat"
        assert bidResponse.seatbid.seat == [OPENX_ALIAS]

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about name and status"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == ModuleName.PB_RULE_ENGINE.code
        assert result.status == SUCCESS

        and: "Analytics result detail info"
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        verifyAll(result.results[0]) {
            impResult.status == SUCCESS
            impResult.values.analyticsKey == groups.analyticsKey
            impResult.values.modelVersion == groups.version
            impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
            impResult.values.resultFunction == groups.rules.first.results.first.function.value
            impResult.values.conditionFired == groups.rules.first.conditions.first
            impResult.values.biddersRemoved.sort() == [GENERIC, OPENX, AMX]
            impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            impResult.appliedTo.impIds == bidRequest.imp.id
        }

        and: "Response should populate seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == [OPENX, AMX, GENERIC].sort()
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should remove hard alias bidder from imps when hard alias bidder excluded in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(Imp.defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                updateImpWithOpenXAndAmxAndOpenXAliasBidder(it)
            }
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(OPENX_ALIAS)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about name and status"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == ModuleName.PB_RULE_ENGINE.code
        assert result.status == SUCCESS

        and: "Analytics result detail info"
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        verifyAll(result.results[0]) {
            impResult.status == SUCCESS
            impResult.values.analyticsKey == groups.analyticsKey
            impResult.values.modelVersion == groups.version
            impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
            impResult.values.resultFunction == groups.rules.first.results.first.function.value
            impResult.values.conditionFired == groups.rules.first.conditions.first
            impResult.values.biddersRemoved.sort() == groups.rules.first.results.first.args.bidders.sort()
            impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            impResult.appliedTo.impIds == [bidRequest.imp[1].id]
        }

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX_ALIAS
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should leave only soft alias bidder at imps when soft alias bidder include in account config"() {
        given: "Bid request with multiply imps bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(Imp.defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                getAliasAndAmxAndOpenXAndWithoutGenericBidder(it)
            }
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(ALIAS)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seat"
        assert bidResponse.seatbid.seat == [ALIAS]

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about name and status"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == ModuleName.PB_RULE_ENGINE.code
        assert result.status == SUCCESS

        and: "Analytics result detail info"
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        verifyAll(result.results[0]) {
            impResult.status == SUCCESS
            impResult.values.analyticsKey == groups.analyticsKey
            impResult.values.modelVersion == groups.version
            impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
            impResult.values.resultFunction == groups.rules.first.results.first.function.value
            impResult.values.conditionFired == groups.rules.first.conditions.first
            impResult.values.biddersRemoved.sort() == [OPENX, GENERIC, AMX].sort()
            impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            impResult.appliedTo.impIds == bidRequest.imp.id
        }

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == ALIAS
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should remove soft alias bidder from imps when soft alias bidder excluded in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(Imp.defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                getAliasAndAmxAndOpenXAndWithoutGenericBidder(it)
            }
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(ALIAS)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about name and status"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == ModuleName.PB_RULE_ENGINE.code
        assert result.status == SUCCESS

        and: "Analytics result detail info"
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        verifyAll(result.results[0]) {
            impResult.status == SUCCESS
            impResult.values.analyticsKey == groups.analyticsKey
            impResult.values.modelVersion == groups.version
            impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
            impResult.values.resultFunction == groups.rules.first.results.first.function.value
            impResult.values.conditionFired == groups.rules.first.conditions.first
            impResult.values.biddersRemoved == [ALIAS]
            impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            impResult.appliedTo.impIds == [bidRequest.imp[1].id]
        }

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == ALIAS
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    private static void updateImpWithOpenXAndAmxAndOpenXAliasBidder(Bidder bidder) {
        bidder.tap {
            openx = Openx.defaultOpenx
            amx = new Amx()
            openxAlias = Openx.defaultOpenx
        }
    }

    private static void getAliasAndAmxAndOpenXAndWithoutGenericBidder(Bidder bidder) {
        bidder.tap {
            alias = new Generic()
            generic = null
            amx = new Amx()
            openx = Openx.defaultOpenx
        }
    }
}
