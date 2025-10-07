package org.prebid.server.functional.tests.module.pbruleengine

import org.prebid.server.functional.model.config.RuleEngineFunctionArgs
import org.prebid.server.functional.model.config.RuleEngineModelSchema
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.ModuleName.PB_RULE_ENGINE
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.config.PbRulesEngine.createRulesEngineWithRule
import static org.prebid.server.functional.model.config.RuleEngineFunction.DATA_CENTER
import static org.prebid.server.functional.model.config.RuleEngineFunction.DATA_CENTER_IN
import static org.prebid.server.functional.model.request.auction.FetchStatus.SUCCESS
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

class RuleEngineInfrastructureSpec extends RuleEngineBaseSpec {

    def "PBS should reject processing rule engine when dataCenterIn schema function args contain invalid data"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DATA_CENTER_IN
                it.args = new RuleEngineFunctionArgs(countries: [CONFIG_DATA_CENTER])
            }
        }

        and: "Account with rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilFailedParsedAndCacheAccount(bidRequest)

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

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, INVALID_CONFIGURATION_FOR_STRINGS_LOG_WARNING(bidRequest.accountId, DATA_CENTER_IN))
    }

    def "PBS should exclude bidder when dataCenterIn match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DATA_CENTER_IN
                it.args = new RuleEngineFunctionArgs(datacenters: [CONFIG_DATA_CENTER])
            }
        }

        and: "Account with rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
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
            it.values.biddersRemoved.sort() == groups.rules.first.results.first.args.bidders.sort()
            it.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            it.appliedTo.impIds == bidRequest.imp.id
        }

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when dataCentersIn not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DATA_CENTER_IN
                it.args = new RuleEngineFunctionArgs(datacenters: [PBSUtils.randomString])
            }
        }

        and: "Account with rules engine"
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

    def "PBS should exclude bidder when dataCenter match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: DATA_CENTER)]
                rules[0].conditions = [CONFIG_DATA_CENTER]
            }
        }

        and: "Account with rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
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
            it.values.biddersRemoved.sort() == groups.rules.first.results.first.args.bidders.sort()
            it.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
            it.appliedTo.impIds == bidRequest.imp.id
        }

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when dataCenter not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: DATA_CENTER)]
                rules[0].conditions = [PBSUtils.randomString]
            }
        }

        and: "Account with rules engine"
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
}
