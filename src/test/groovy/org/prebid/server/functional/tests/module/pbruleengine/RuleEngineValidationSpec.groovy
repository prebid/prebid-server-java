package org.prebid.server.functional.tests.module.pbruleengine

import static org.prebid.server.functional.model.config.PbRulesEngine.createRulesEngineWithRule
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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithRulesEngineModule)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

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
        def metrics = pbsServiceWithRulesEngineModule.sendCollectedMetricsRequest()
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

    def "PBS shouldn't remove bidder when rule engine not fully configured in account wihout rule conditions"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with enabled rules engine"
        def pbRuleEngine = createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].rules[0].conditions = [] }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.getAccountId(), pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithRulesEngineModule)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

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

        and: "PBs should populate failer metrics"
        def metrics = pbsServiceWithRulesEngineModule.sendCollectedMetricsRequest()
        assert metrics[FAILER_METRIC] == 1

        and: "PBs shouldn't contain noop and call metrics"
        assert !metrics[CALL_METRIC]
        assert !metrics[NOOP_METRIC]
        assert !metrics[UPDATE_METRIC]

        and: "Invocation result should contain warning of rule engine"
        assert getInvocationResult(bidResponse)[0].message == "No matching rule found"
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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        and: "Flush metrics"
        flushMetrics(pbsServiceWithRulesEngineModule)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

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
        def metrics = pbsServiceWithRulesEngineModule.sendCollectedMetricsRequest()
        assert metrics[FAILER_METRIC] == 1

        and: "PBs shouldn't contain noop and call metrics"
        assert !metrics[CALL_METRIC]
        assert !metrics[NOOP_METRIC]
        assert !metrics[UPDATE_METRIC]
    }

    def "PBS shouldn't remove bidder and emit a warning when model group rule engine not fully configured in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with enabled rules engine"
        def pbRulesEngine = createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups = [] }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.getAccountId(), pbRulesEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "Invocation result should contain warning of rule engine"
        assert getInvocationResult(bidResponse)[0].message == "Rule for account ${bidRequest.accountId} is not ready"
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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

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

    def "PBS shouldn't remove bidder when rule engine disabled or absent in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

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

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

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
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule()
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

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

        and: "Invocation result should contain warning of rule engine"
        assert getInvocationResult(bidResponse)[0].message == "Rule for account ${bidRequest.accountId} is not ready"
    }

}
