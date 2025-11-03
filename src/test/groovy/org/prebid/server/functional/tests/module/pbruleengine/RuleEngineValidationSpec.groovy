package org.prebid.server.functional.tests.module.pbruleengine

import org.prebid.server.functional.model.config.RuleEngineFunctionArgs
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.config.PbRulesEngine.createRulesEngineWithRule
import static org.prebid.server.functional.model.config.RuleEngineFunction.AD_UNIT_CODE
import static org.prebid.server.functional.model.config.RuleEngineFunction.BUNDLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.CHANNEL
import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_COUNTRY
import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_TYPE
import static org.prebid.server.functional.model.config.RuleEngineFunction.DOMAIN
import static org.prebid.server.functional.model.config.RuleEngineFunction.EID_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.FPD_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.GPP_SID_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.TCF_IN_SCOPE
import static org.prebid.server.functional.model.config.RuleEngineFunction.USER_FPD_AVAILABLE
import static org.prebid.server.functional.model.pricefloors.Country.BULGARIA

class RuleEngineValidationSpec extends RuleEngineBaseSpec {

    def "PBS shouldn't remove bidder when rule engine not fully configured in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with enabled rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.getAccountId(), pbRulesEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "PBs should populate call and noop metrics"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[CALL_METRIC] == 1
        assert metrics[NOOP_METRIC] == 1

        and: "PBs should populate update metrics"
        assert !metrics[UPDATE_METRIC]

        where:
        pbRulesEngine << [
                createRulesEngineWithRule().tap { it.ruleSets = [] },
                createRulesEngineWithRule().tap { it.ruleSets[0].stage = null },
                createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].schema = [] },
                createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].rules = [] },
                createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].rules[0].results = [] }
        ]
    }

    def "PBS shouldn't remove bidder when rule engine not fully configured in account without rule conditions"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with enabled rules engine"
        def pbRuleEngine = createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].rules[0].conditions = [] }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.getAccountId(), pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "PBs should populate noop metrics"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[NOOP_METRIC] == 1
    }

    def "PBS shouldn't remove bidder and emit a warning when args rule engine not fully configured in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with enabled rules engine"
        def pbRuleEngine = createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].rules[0].results[0].args = null }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.getAccountId(), pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilFailedParsedAndCacheAccount(bidRequest)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithMultipleModules)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "PBs should populate failer metrics"
        def metrics = pbsServiceWithMultipleModules.sendCollectedMetricsRequest()
        assert metrics[NOOP_METRIC] == 1
    }

    def "PBS shouldn't remove bidder and emit a warning when model group rule engine not fully configured in account"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with enabled rules engine"
        def pbRulesEngine = createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups = [] }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.getAccountId(), pbRulesEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilFailedParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "PBs should emit failed logs"
        def logs = pbsServiceWithMultipleModules.getLogsByTime(startTime)
        assert getLogsByText(logs, "Failed to parse rule-engine config for account $bidRequest.accountId:" +
                " Weighted list cannot be empty")
    }

    def "PBS shouldn't log default model when rule does not fired and empty model default"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it, BULGARIA)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].modelDefault = null
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

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

    def "PBS shouldn't remove bidder when rule engine disabled or absent in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        where:
        pbRuleEngine << [createRulesEngineWithRule(false), null]
    }

    def "PBS shouldn't remove bidder when rule sets disabled in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with disabled rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets.first.enabled = false
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "Analytics result shouldn't contain info about module exclude"
        assert !getAnalyticResults(bidResponse)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid
    }

    def "PBS shouldn't remove any bidder without cache account request"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule()
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "Analytics result shouldn't contain info about module exclude"
        assert !getAnalyticResults(bidResponse)

        and: "PBs should emit failed logs"
        def logs = pbsServiceWithMultipleModules.getLogsByTime(startTime)
        assert getLogsByText(logs, "Parsing rule for account $bidRequest.accountId").size() == 1
    }

    def "PBS shouldn't take rule with higher weight and not remove bidder when weight negative or zero"() {
        given: "Start up time"
        def start = Instant.now()

        and: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with few model group"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                it.weight = weight
            }
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilFailedParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

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

        and: "PBS should emit log"
        def logsByTime = pbsServiceWithMultipleModules.getLogsByTime(start)
        assert getLogsByText(logsByTime, "Failed to parse rule-engine config for account $bidRequest.accountId:" +
                " Weight must be greater than zero")

        where:
        weight << [PBSUtils.randomNegativeNumber, 0]
    }

    def "PBS should reject processing rule engine when #function schema function contain args"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = function
                it.args = RuleEngineFunctionArgs.defaultFunctionArgs
            }
        }

        and: "Save account with rule engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilFailedParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Logs should contain error"
        def logs = pbsServiceWithMultipleModules.getLogsByTime(startTime)
        assert getLogsByText(logs, "Failed to parse rule-engine config for account ${bidRequest.accountId}: " +
                "Function '${function.value}' configuration is invalid: No arguments allowed")

        where:
        function << [DEVICE_TYPE, AD_UNIT_CODE, BUNDLE, DOMAIN, TCF_IN_SCOPE, GPP_SID_AVAILABLE, FPD_AVAILABLE,
                     USER_FPD_AVAILABLE, EID_AVAILABLE, CHANNEL, DEVICE_COUNTRY]
    }
}
