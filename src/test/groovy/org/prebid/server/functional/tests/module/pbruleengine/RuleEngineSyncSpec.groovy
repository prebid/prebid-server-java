package org.prebid.server.functional.tests.module.pbruleengine

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.util.HttpUtil

import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.config.PbRulesEngine.createRulesEngineWithRule
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithExcludeResult
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithIncludeResult
import static org.prebid.server.functional.model.request.auction.FetchStatus.SUCCESS
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

class RuleEngineSyncSpec extends RuleEngineBaseSpec {

    def "PBS shouldn't remove bidder from imps when bidder has ID in the uids cookie and bidder excluded and ifSyncedId=true in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC)]
            it.ruleSets[0].modelGroups[0].rules[0].results[0].args.ifSyncedId = true
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED
        assert bidResponse.seatbid.seat.sort() == [GENERIC, OPENX, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)
    }

    def "PBS should remove bidder from imps when bidder has ID in the uids cookie and bidder excluded and ifSyncedId=false in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC)]
            it.ruleSets[0].modelGroups[0].rules[0].results[0].args.ifSyncedId = false
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [OPENX, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_MODULE_NAME_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved.sort() == groups.rules.first.results.first.args.bidders.sort()
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == bidRequest.imp.id

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't remove bidder from imps when bidder hasn't ID in the uids cookie and bidder excluded and ifSyncedId=true in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC)]
            it.ruleSets[0].modelGroups[0].rules[0].results[0].args.ifSyncedId = true
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED
        assert bidResponse.seatbid.seat.sort() == [GENERIC, OPENX, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "Analytics result shouldn't contain info about module exclude"
        assert !getAnalyticResults(bidResponse)
    }

    def "PBS should remove requested bidders at imps when bidder has ID in the uids cookie and bidder include and ifSyncedId=true in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(GENERIC)]
            it.ruleSets[0].modelGroups[0].rules[0].results[0].args.ifSyncedId = true
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bid response shouldn't contain seat"
        assert !bidResponse.seatbid.seat

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about name and status"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_MODULE_NAME_CODE
        assert result.status == SUCCESS

        and: "Analytics result detail info"
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        verifyAll {
            impResult.status == SUCCESS
            impResult.values.analyticsKey == groups.analyticsKey
            impResult.values.modelVersion == groups.version
            impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
            impResult.values.resultFunction == groups.rules.first.results.first.function.value
            impResult.values.conditionFired == groups.rules.first.conditions.first
            impResult.values.biddersRemoved.sort() == [AMX, OPENX, GENERIC].sort()
            impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            impResult.appliedTo.impIds == bidRequest.imp.id
        }

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == [OPENX, GENERIC]
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should leave only include bidder at imps when bidder has ID in the uids cookie and bidder include and ifSyncedId=false in account config"() {
        given: "Bid request with multiply imps bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(GENERIC)]
            it.ruleSets[0].modelGroups[0].rules[0].results[0].args.ifSyncedId = false
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cookies headers"
        def uidsCookie = UidsCookie.defaultUidsCookie
        def cookieHeader = HttpUtil.getCookieHeader(uidsCookie)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bid response should contain seat"
        assert bidResponse.seatbid.size() == ONE_BIDDER_REQUESTED
        assert bidResponse.seatbid.seat == [GENERIC]

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about name and status"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_MODULE_NAME_CODE
        assert result.status == SUCCESS

        and: "Analytics result detail info"
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        verifyAll {
            impResult.status == SUCCESS
            impResult.values.analyticsKey == groups.analyticsKey
            impResult.values.modelVersion == groups.version
            impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
            impResult.values.resultFunction == groups.rules.first.results.first.function.value
            impResult.values.conditionFired == groups.rules.first.conditions.first
            impResult.values.biddersRemoved.sort() == [OPENX, AMX]
            impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            impResult.appliedTo.impIds == bidRequest.imp.id
        }

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == [OPENX, GENERIC]
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should leave request bidder at imps when bidder hasn't ID in the uids cookie and bidder excluded and ifSyncedId=true in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(GENERIC)]
            it.ruleSets[0].modelGroups[0].rules[0].results[0].args.ifSyncedId = true
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED
        assert bidResponse.seatbid.seat.sort() == [GENERIC, OPENX, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "Analytics result shouldn't contain info about module exclude"
        assert !getAnalyticResults(bidResponse)
    }
}
