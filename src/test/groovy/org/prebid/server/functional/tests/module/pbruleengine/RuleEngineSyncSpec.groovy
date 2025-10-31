package org.prebid.server.functional.tests.module.pbruleengine

import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.util.HttpUtil

import static org.prebid.server.functional.model.config.ModuleName.PB_RULE_ENGINE
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.config.PbRulesEngine.createRulesEngineWithRule
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithExcludeResult
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithIncludeResult
import static org.prebid.server.functional.model.request.auction.FetchStatus.SUCCESS
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

class RuleEngineSyncSpec extends RuleEngineBaseSpec {

    def "PBS should remove bidder from imps when bidder has ID in the uids cookie and bidder excluded and ifSyncedId=true in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC, true)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cookies headers"
        def cookieHeader = HttpUtil.getCookieHeader(UidsCookie.defaultUidsCookie)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == [OPENX, AMX]

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE.code
        assert result.status == SUCCESS

        and: "Analytics result detail info"
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        verifyAll(result.results[0]) {
            it.status == SUCCESS
            it.values.analyticsKey == groups.analyticsKey
            it.values.modelVersion == groups.version
            it.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
            it.values.resultFunction == groups.rules.first.results.first.function.value
            it.values.conditionFired == groups.rules.first.conditions.first
            it.values.biddersRemoved.sort() == groups.rules.first.results.first.args.bidders.sort()
            it.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            it.appliedTo.impIds == bidRequest.imp.id
        }

        and: "Response should contain seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't remove bidder from imps when bidder has ID in the uids cookie and bidder excluded and ifSyncedId=false in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC, false)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cookies headers"
        def cookieHeader = HttpUtil.getCookieHeader(UidsCookie.defaultUidsCookie)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

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

    def "PBS shouldn't remove bidder from imps when bidder hasn't ID in the uids cookie and bidder excluded and ifSyncedId=true in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC, true)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

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
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(GENERIC, true)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cookies headers"
        def cookieHeader = HttpUtil.getCookieHeader(UidsCookie.defaultUidsCookie)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest, cookieHeader)

        then: "Bid response shouldn't contain seat"
        assert bidResponse.seatbid.seat == [GENERIC]

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about name and status"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE.code
        assert result.status == SUCCESS

        and: "Analytics result detail info"
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        verifyAll(result.results[0]) {
            it.status == SUCCESS
            it.values.analyticsKey == groups.analyticsKey
            it.values.modelVersion == groups.version
            it.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
            it.values.resultFunction == groups.rules.first.results.first.function.value
            it.values.conditionFired == groups.rules.first.conditions.first
            it.values.biddersRemoved.sort() == [AMX, OPENX].sort()
            it.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            it.appliedTo.impIds == bidRequest.imp.id
        }

        and: "Response should contain seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 2
        def seatNonBid = bidResponse.ext.seatnonbid
        assert seatNonBid.seat.sort() == [OPENX, AMX].sort()
        assert seatNonBid.nonBid.impId.flatten().unique().sort() == bidRequest.imp.id.sort()
        assert seatNonBid.nonBid.statusCode.flatten().unique() == [REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE]
    }

    def "PBS shouldn't include bidder at imps when bidder has ID in the uids cookie and bidder include and ifSyncedId=false in account config"() {
        given: "Bid request with multiply imps bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(GENERIC, false)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cookies headers"
        def cookieHeader = HttpUtil.getCookieHeader(UidsCookie.defaultUidsCookie)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

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
        assert result.name == PB_RULE_ENGINE.code
        assert result.status == SUCCESS

        and: "Analytics result detail info"
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        verifyAll(result.results[0]) {
            it.status == SUCCESS
            it.values.analyticsKey == groups.analyticsKey
            it.values.modelVersion == groups.version
            it.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
            it.values.resultFunction == groups.rules.first.results.first.function.value
            it.values.conditionFired == groups.rules.first.conditions.first
            it.values.biddersRemoved.sort() == [OPENX, AMX, GENERIC].sort()
            it.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            it.appliedTo.impIds == bidRequest.imp.id
        }

        and: "Response shouldn't contain seatNon bid with code 203"
        assert !bidResponse.ext.seatnonbid
    }

    def "PBS should leave request bidder at imps when bidder hasn't ID in the uids cookie and bidder excluded and ifSyncedId=true in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(GENERIC, true)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

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
