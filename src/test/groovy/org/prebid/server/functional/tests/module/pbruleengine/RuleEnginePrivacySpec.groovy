package org.prebid.server.functional.tests.module.pbruleengine

import org.prebid.server.functional.model.config.RuleEngineFunctionArgs
import org.prebid.server.functional.model.config.RuleEngineModelSchema
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.model.request.auction.AppExt
import org.prebid.server.functional.model.request.auction.AppExtData
import org.prebid.server.functional.model.request.auction.Content
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.model.request.auction.SiteExt
import org.prebid.server.functional.model.request.auction.SiteExtData
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.model.response.auction.AnalyticTagStatus
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfConsent

import java.time.Instant

import static org.prebid.server.functional.model.config.ModuleName.PB_RULE_ENGINE
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.config.PbRulesEngine.createRulesEngineWithRule
import static org.prebid.server.functional.model.config.RuleEngineFunction.EID_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.EID_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.FPD_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.GPP_SID_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.GPP_SID_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.TCF_IN_SCOPE
import static org.prebid.server.functional.model.config.RuleEngineFunction.USER_FPD_AVAILABLE
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.response.auction.AnalyticTagStatus.SUCCESS
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

class RuleEnginePrivacySpec extends RuleEngineBaseSpec {

    def "PBS should exclude bidder when eidAvailable match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            user = new User(eids: [Eid.getDefaultEid()])
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: EID_AVAILABLE)]
        }

        and: "Account with rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

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

    def "PBS shouldn't exclude bidder when eidAvailable not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            user = new User(eids: eids)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema[0].function = EID_AVAILABLE
                rules[0].conditions = ["TRUE"]
            }
        }

        and: "Account with rules engine"
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

        where:
        eids << [null, []]
    }

    def "PBS should reject processing rule engine when eidIn schema function args contain invalid data"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = EID_IN
                it.args = new RuleEngineFunctionArgs(sources: [PBSUtils.randomNumber])
            }
        }

        and: "Account with rules engine"
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

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about module exclude"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithMultipleModules.getLogsByTime(startTime)
        assert getLogsByText(logs, INVALID_CONFIGURATION_FOR_STRINGS_LOG_WARNING(bidRequest.accountId, EID_IN))
    }

    def "PBS should exclude bidder when eidIn match with condition"() {
        given: "Bid request with multiply bidders"
        def eid = Eid.getDefaultEid()
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            user = new User(eids: [eid])
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = EID_IN
                it.args = new RuleEngineFunctionArgs(sources: [PBSUtils.randomString, eid.source, PBSUtils.randomString])
            }
        }

        and: "Account with rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

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

    def "PBS shouldn't exclude bidder when eidIn not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            user = new User(eids: [Eid.getDefaultEid()])
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = EID_IN
                it.args = new RuleEngineFunctionArgs(sources: [PBSUtils.randomString, PBSUtils.randomString])
            }
        }

        and: "Account with rules engine"
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

    def "PBS should exclude bidder when userFpdAvailable match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            user = requestedUfpUser
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: USER_FPD_AVAILABLE)]
        }

        and: "Account with rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

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
        requestedUfpUser << [new User(data: [Data.defaultData], ext: new UserExt(data: UserExtData.FPDUserExtData)),
                             new User(ext: new UserExt(data: UserExtData.FPDUserExtData)),
                             new User(data: [Data.defaultData])]
    }

    def "PBS shouldn't exclude bidder when userFpdAvailable not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            user = requestedUfpUser
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: USER_FPD_AVAILABLE)]
        }

        and: "Account with rules engine"
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

        where:
        requestedUfpUser << [new User(data: null), new User(data: [null]),
                             new User(ext: new UserExt(data: null)),
                             new User(data: null, ext: new UserExt(data: null))
        ]
    }

    def "PBS should exclude bidder when fpdAvailable match with condition"() {
        given: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: FPD_AVAILABLE)]
        }

        and: "Account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

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
        bidRequest << [
                getDefaultBidRequestWithMultiplyBidders().tap {
                    user = new User(data: [Data.defaultData])
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    user = new User(ext: new UserExt(data: UserExtData.FPDUserExtData))
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    site.content = new Content(data: [Data.defaultData])
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    site.ext = new SiteExt(data: SiteExtData.FPDSiteExtData)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    app.content = new Content(data: [Data.defaultData])
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    app.ext = new AppExt(data: new AppExtData(language: PBSUtils.randomString))
                }
        ]
    }

    def "PBS shouldn't exclude bidder when fpdAvailable not match with condition"() {
        given: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: FPD_AVAILABLE)]
        }

        and: "Account with rules engine"
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

        where:
        bidRequest << [
                getDefaultBidRequestWithMultiplyBidders().tap {
                    user = new User(data: null, ext: new UserExt(data: null))
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    user = new User(ext: new UserExt(data: null))
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    site.content = new Content(data: [null])
                    site.ext = new SiteExt(data: null)
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    site.ext = new SiteExt(data: null)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    app.content = new Content(data: [null])
                    app.ext = new AppExt(data: null)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    app.ext = new AppExt(data: null)
                }
        ]
    }

    def "PBS should exclude bidder when gppSidAvailable match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            regs = new Regs(gppSid: [PBSUtils.getRandomEnum(GppSectionId).getIntValue()])
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: GPP_SID_AVAILABLE)]
        }

        and: "Account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

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

    def "PBS shouldn't exclude bidder when gppSidAvailable not match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            regs = new Regs(gppSid: gppSid)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: GPP_SID_AVAILABLE)]
        }

        and: "Account with rules engine"
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

        where:
        gppSid << [[PBSUtils.randomNegativeNumber], null]
    }

    def "PBS should reject processing rule engine when gppSidIn schema function args contain invalid data"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            regs = new Regs(gdpr: 0, gppSid: [PBSUtils.getRandomEnum(GppSectionId, [GppSectionId.TCF_EU_V2]).getIntValue()])
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = GPP_SID_IN
                it.args = new RuleEngineFunctionArgs(sids: [PBSUtils.randomString])
            }
        }

        and: "Account with rules engine"
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

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithMultipleModules.getLogsByTime(startTime)
        assert getLogsByText(logs, INVALID_CONFIGURATION_FOR_INTEGERS_LOG_WARNING(bidRequest.accountId, GPP_SID_IN))
    }

    def "PBS should exclude bidder when gppSidIn match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            regs = new Regs(gppSid: [gppSectionId.getIntValue()])
        }

        and: "Create rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = GPP_SID_IN
                it.args = new RuleEngineFunctionArgs(sids: [gppSectionId])
            }
        }

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

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
            it.values.biddersRemoved == groups.rules.first.results.first.args.bidders
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
        gppSectionId << GppSectionId.values() - GppSectionId.TCF_EU_V2
    }

    def "PBS shouldn't exclude bidder when gppSidIn not match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            regs = new Regs(gppSid: [gppSectionId.getIntValue()])
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = GPP_SID_IN
                it.args = new RuleEngineFunctionArgs(sids: [PBSUtils.getRandomEnum(GppSectionId, [gppSectionId]).getIntValue()])
            }
        }

        and: "Save account with disabled or without rules engine"
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

        where:
        gppSectionId << GppSectionId.values() - GppSectionId.TCF_EU_V2
    }

    def "PBS should exclude bidder when tcfInScope match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            regs = new Regs(gdpr: gdpr)
            user = new User(ext: new UserExt(consent: new TcfConsent.Builder()
                    .setPurposesLITransparency(BASIC_ADS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()))
        }

        and: "Create rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: TCF_IN_SCOPE)]
                rules[0].conditions = [condition]
            }
        }

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        waitUntilSuccessfullyParsedAndCacheAccount(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

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
            it.values.biddersRemoved == groups.rules.first.results.first.args.bidders
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
        gdpr | condition
        1    | 'true'
        0    | 'false'
    }

    def "PBS shouldn't exclude bidder when tcfInScope not match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            regs = new Regs(gdpr: gdpr)
            user = new User(ext: new UserExt(consent: new TcfConsent.Builder()
                    .setPurposesLITransparency(BASIC_ADS)
                    .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                    .build()))
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: TCF_IN_SCOPE)]
                rules[0].conditions = [condition]
            }
        }

        and: "Save account with disabled or without rules engine"
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

        where:
        gdpr | condition
        0    | 'true'
        1    | 'false'
    }
}
