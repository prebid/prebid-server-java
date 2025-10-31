package org.prebid.server.functional.tests.module.pbruleengine

import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.config.RuleEngineFunctionArgs
import org.prebid.server.functional.model.config.RuleEngineModelSchema
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.pricefloors.MediaType
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExtContextData
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.Publisher
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import java.time.Instant

import static org.prebid.server.functional.model.ChannelType.WEB
import static org.prebid.server.functional.model.config.ModuleName.PB_RULE_ENGINE
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.config.PbRulesEngine.createRulesEngineWithRule
import static org.prebid.server.functional.model.config.RuleEngineFunction.AD_UNIT_CODE
import static org.prebid.server.functional.model.config.RuleEngineFunction.AD_UNIT_CODE_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.BUNDLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.BUNDLE_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.CHANNEL
import static org.prebid.server.functional.model.config.RuleEngineFunction.DOMAIN
import static org.prebid.server.functional.model.config.RuleEngineFunction.DOMAIN_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.MEDIA_TYPE_IN
import static org.prebid.server.functional.model.pricefloors.MediaType.BANNER
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.FetchStatus.SUCCESS
import static org.prebid.server.functional.model.request.auction.ImpUnitCode.GPID
import static org.prebid.server.functional.model.request.auction.ImpUnitCode.PB_AD_SLOT
import static org.prebid.server.functional.model.request.auction.ImpUnitCode.STORED_REQUEST
import static org.prebid.server.functional.model.request.auction.ImpUnitCode.TAG_ID
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

class RuleEngineContextSpec extends RuleEngineBaseSpec {

    def "PBS should exclude bidder when channel match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: CHANNEL)]
                rules[0].conditions = [WEB.value]
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

    def "PBS shouldn't exclude bidder when channel not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: CHANNEL)]
                rules[0].conditions = [PBSUtils.getRandomEnum(ChannelType, [WEB]).value]
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

    def "PBS should exclude bidder when domain match with condition"() {
        given: "Default bid request with multiply bidder"
        def randomDomain = PBSUtils.randomString
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(distributionChannel).tap {
            updatePublisherDomain(it, distributionChannel, randomDomain)
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: DOMAIN)]
                rules[0].conditions = [randomDomain]
            }
        }

        and: "Save account with rule engine config"
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

        where:
        distributionChannel << DistributionChannel.values()
    }

    def "PBS shouldn't exclude bidder when domain not match with condition"() {
        given: "Default bid request with random domain"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(distributionCahannel).tap {
            updatePublisherDomain(it, distributionCahannel, PBSUtils.randomString)
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: DOMAIN)]
                rules[0].conditions = [PBSUtils.randomString]
            }
        }

        and: "Save account with disabled or without rules engine"
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

        where:
        distributionCahannel << DistributionChannel.values()
    }

    def "PBS should reject processing the rule engine when the domainIn schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiplyB bidders"
        def bidRequest = bidRequestWithDomaint

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DOMAIN_IN
                it.args = new RuleEngineFunctionArgs(domains: [PBSUtils.randomNumber])
            }
        }

        and: "Save account with rule engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilFailedParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, INVALID_CONFIGURATION_FOR_STRINGS_LOG_WARNING(bidRequest.accountId, DOMAIN_IN))

        where:
        bidRequestWithDomaint << [
                getDefaultBidRequestWithMultiplyBidders(SITE).tap {
                    it.site.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(SITE).tap {
                    it.site.domain = PBSUtils.randomString
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    it.app.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    it.app.domain = PBSUtils.randomString
                },
                getDefaultBidRequestWithMultiplyBidders(DOOH).tap {
                    it.dooh.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(DOOH).tap {
                    it.dooh.domain = PBSUtils.randomString
                }]
    }

    def "PBS should exclude bidder when domainIn match with condition"() {
        given: "Default bid request with multiply bidder"
        def randomDomain = PBSUtils.randomString
        def bidRequest = createBidRequestWithDomains(type, randomDomain, usePublisher)

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DOMAIN_IN
                it.args = new RuleEngineFunctionArgs(domains: [PBSUtils.randomString, randomDomain])
            }
        }

        and: "Save account with rule engine config"
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

        where:
        type | usePublisher
        SITE | true
        SITE | false
        APP  | true
        APP  | false
        DOOH | true
        DOOH | false
    }

    def "PBS shouldn't exclude bidder when domainIn not match with condition"() {
        given: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DOMAIN_IN
                it.args = new RuleEngineFunctionArgs(domains: [PBSUtils.randomString, PBSUtils.randomString])
            }
        }

        and: "Save account with disabled or without rules engine"
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

        where:
        bidRequest << [
                getDefaultBidRequestWithMultiplyBidders(SITE).tap {
                    it.site.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(SITE).tap {
                    it.site.domain = PBSUtils.randomString
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    it.app.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    it.app.domain = PBSUtils.randomString
                },
                getDefaultBidRequestWithMultiplyBidders(DOOH).tap {
                    it.dooh.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(DOOH).tap {
                    it.dooh.domain = PBSUtils.randomString
                }]
    }

    def "PBS should exclude bidder when bundle match with condition"() {
        given: "Default bid request with multiply bidder"
        def bundle = PBSUtils.randomString
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            app.bundle = bundle
        }

        and: "Create rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: BUNDLE)]
                rules[0].conditions = [bundle]
            }
        }

        and: "Save account with rule engine config"
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

    def "PBS shouldn't exclude bidder when bundle not match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            app.bundle = PBSUtils.randomString
        }

        and: "Create rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: BUNDLE)]
                rules[0].conditions = [PBSUtils.randomString]
            }
        }

        and: "Save account with disabled or without rules engine"
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

    def "PBS should reject processing the rule engine when the bundleIn schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            app.bundle = PBSUtils.randomString
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = BUNDLE_IN
                it.args = new RuleEngineFunctionArgs(bundles: [PBSUtils.randomNumber])
            }
        }

        and: "Save account with rule engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilFailedParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

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

        then: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, INVALID_CONFIGURATION_FOR_STRINGS_LOG_WARNING(bidRequest.accountId, BUNDLE_IN))
    }

    def "PBS should exclude bidder when bundleIn match with condition"() {
        given: "Default bid request with multiply bidders"
        def bundle = PBSUtils.randomString
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            app.bundle = bundle
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = BUNDLE_IN
                it.args = new RuleEngineFunctionArgs(bundles: [bundle])
            }
        }

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Account cache"
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

    def "PBS shouldn't exclude bidder when bundleIn not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            app.bundle = PBSUtils.randomString
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = BUNDLE_IN
                it.args = new RuleEngineFunctionArgs(bundles: [PBSUtils.randomString, PBSUtils.randomString])
            }
        }

        and: "Save account with rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Account cache"
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

    def "PBS should reject processing the rule engine when the mediaTypeIn schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = MEDIA_TYPE_IN
                it.args = new RuleEngineFunctionArgs(types: [mediaTypeInArgs])
            }
        }

        and: "Save account with rule engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Account cache"
        waitUntilFailedParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        then: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, INVALID_CONFIGURATION_FOR_STRINGS_LOG_WARNING(bidRequest.accountId, MEDIA_TYPE_IN))

        where:
        mediaTypeInArgs << [null, PBSUtils.randomNumber]
    }

    def "PBS should exclude bidder when mediaTypeIn match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Setup bidder response"
        def bidderResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidder.setResponse(bidRequest.id, bidderResponse)

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = MEDIA_TYPE_IN
                it.args = new RuleEngineFunctionArgs(types: [BANNER.value])
            }
        }

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Account cache"
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

    def "PBS shouldn't exclude bidder when mediaTypeIn not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = MEDIA_TYPE_IN
                it.args = new RuleEngineFunctionArgs(types: [PBSUtils.getRandomEnum(MediaType, [BANNER])])
            }
        }

        and: "Save account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Account cache"
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

    def "PBS should exclude bidder when adUnitCode match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            imp[0].ext.gpid = gpid
            imp[0].tagId = tagId
            imp[0].ext.data = new ImpExtContextData(pbAdSlot: pbAdSlot)
            imp[0].ext.prebid.storedRequest = prebidStoredRequest
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: AD_UNIT_CODE)]
                rules[0].conditions = [getImpAdUnitCode(bidRequest.imp[0])]
            }
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest)
        storedImpDao.save(storedImp)

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Account cache"
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

        where:
        gpid                       | tagId                      | pbAdSlot                   | prebidStoredRequest
        PBSUtils.getRandomString() | null                       | null                       | null
        null                       | PBSUtils.getRandomString() | null                       | null
        null                       | null                       | PBSUtils.getRandomString() | null
        null                       | null                       | null                       | new PrebidStoredRequest(id: PBSUtils.getRandomString())
        PBSUtils.getRandomString() | PBSUtils.getRandomString() | PBSUtils.getRandomString() | new PrebidStoredRequest(id: PBSUtils.getRandomString())
        null                       | PBSUtils.getRandomString() | PBSUtils.getRandomString() | new PrebidStoredRequest(id: PBSUtils.getRandomString())
        null                       | null                       | PBSUtils.getRandomString() | new PrebidStoredRequest(id: PBSUtils.getRandomString())
        null                       | null                       | null                       | new PrebidStoredRequest(id: PBSUtils.getRandomString())
    }

    def "PBS shouldn't exclude bidder when adUnitCode not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: AD_UNIT_CODE)]
                rules[0].conditions = [PBSUtils.randomString]
            }
        }

        and: "Save account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Account cache"
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

    def "PBS should reject processing the rule engine when the adUnitCodeIn schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            imp[0].tagId = PBSUtils.randomString
            imp[0].ext.gpid = PBSUtils.randomString
            imp[0].ext.data = new ImpExtContextData(pbAdSlot: PBSUtils.randomString)
            imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impData = Imp.getDefaultImpression()
        }
        storedImpDao.save(storedImp)

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = AD_UNIT_CODE_IN
                it.args = new RuleEngineFunctionArgs(codes: [arguments])
            }
        }

        and: "Save account with rule engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Account cache"
        waitUntilFailedParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.seat.sort() == MULTI_BID_ADAPTERS

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, INVALID_CONFIGURATION_FOR_STRINGS_LOG_WARNING(bidRequest.accountId, AD_UNIT_CODE_IN))

        where:
        arguments << [PBSUtils.randomBoolean, PBSUtils.randomNumber]
    }

    def "PBS should exclude bidder when adUnitCodeIn match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            imp[0].tagId = PBSUtils.randomString
            imp[0].ext.gpid = PBSUtils.randomString
            imp[0].ext.data = new ImpExtContextData(pbAdSlot: PBSUtils.randomString)
            imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = AD_UNIT_CODE_IN
                it.args = new RuleEngineFunctionArgs(codes: [PBSUtils.randomString,
                                                             getImpAdUnitCodeByCode(bidRequest.imp[0], impUnitCode)])
            }
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest)
        storedImpDao.save(storedImp)

        and: "Save account with rule engine config"
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

        where:
        impUnitCode << [TAG_ID, GPID, PB_AD_SLOT, STORED_REQUEST]
    }

    def "PBS shouldn't exclude bidder when adUnitCodeIn not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders()

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = AD_UNIT_CODE_IN
                it.args = new RuleEngineFunctionArgs(codes: [PBSUtils.randomString, PBSUtils.randomString])
            }
        }

        and: "Save account with disabled or without rules engine"
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
