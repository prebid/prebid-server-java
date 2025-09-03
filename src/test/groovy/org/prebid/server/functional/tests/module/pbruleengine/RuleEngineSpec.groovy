package org.prebid.server.functional.tests.module.pbruleengine

import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.UidsCookie
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Openx
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.PbRulesEngine
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.config.RuleEngineFunctionArgs
import org.prebid.server.functional.model.config.RuleEngineModelDefault
import org.prebid.server.functional.model.config.RuleEngineModelDefaultArgs
import org.prebid.server.functional.model.config.RuleEngineModelSchema
import org.prebid.server.functional.model.config.RuleSets
import org.prebid.server.functional.model.config.RulesEngineModelGroups
import org.prebid.server.functional.model.config.Stage
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.pricefloors.MediaType
import org.prebid.server.functional.model.request.GppSectionId
import org.prebid.server.functional.model.request.auction.Amx
import org.prebid.server.functional.model.request.auction.AppExt
import org.prebid.server.functional.model.request.auction.AppExtData
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Content
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceType
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.ImpExtContextData
import org.prebid.server.functional.model.request.auction.Kvps
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.Publisher
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.model.request.auction.SiteExt
import org.prebid.server.functional.model.request.auction.SiteExtData
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.model.response.auction.BidRejectionReason
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.HttpUtil
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfConsent
import spock.lang.IgnoreRest

import java.time.Instant

import static java.lang.Boolean.FALSE
import static java.lang.Boolean.TRUE
import static org.prebid.server.functional.model.ChannelType.WEB
import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.AMX
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.OPENX
import static org.prebid.server.functional.model.bidder.BidderName.OPENX_ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.UNKNOWN
import static org.prebid.server.functional.model.config.PbRulesEngine.createRulesEngineWithRule
import static org.prebid.server.functional.model.config.ResultFunction.LOG_A_TAG
import static org.prebid.server.functional.model.config.RuleEngineFunction.AD_UNIT_CODE
import static org.prebid.server.functional.model.config.RuleEngineFunction.AD_UNIT_CODE_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.BUNDLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.BUNDLE_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.CHANNEL
import static org.prebid.server.functional.model.config.RuleEngineFunction.DATA_CENTER
import static org.prebid.server.functional.model.config.RuleEngineFunction.DATA_CENTER_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_COUNTRY
import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_COUNTRY_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_TYPE
import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_TYPE_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.DOMAIN
import static org.prebid.server.functional.model.config.RuleEngineFunction.DOMAIN_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.EID_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.EID_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.FPD_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.GPP_SID_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineFunction.GPP_SID_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.MEDIA_TYPE_IN
import static org.prebid.server.functional.model.config.RuleEngineFunction.PERCENT
import static org.prebid.server.functional.model.config.RuleEngineFunction.PREBID_KEY
import static org.prebid.server.functional.model.config.RuleEngineFunction.TCF_IN_SCOPE
import static org.prebid.server.functional.model.config.RuleEngineFunction.USER_FPD_AVAILABLE
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithExcludeResult
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithIncludeResult
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithLogATagResult
import static org.prebid.server.functional.model.pricefloors.Country.BULGARIA
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.pricefloors.MediaType.BANNER
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.request.auction.FetchStatus.SUCCESS
import static org.prebid.server.functional.model.request.auction.Imp.getDefaultImpression
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.ERROR_NO_BID
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

class RuleEngineSpec extends ModuleBaseSpec {

    private static final Integer BIDDERS_REQUESTED = 3
    private static final Integer ONE_BIDDER_REQUESTED = 1
    private static final String APPLIED_FOR_ALL_IMPS = "*"
    private static final String DEFAULT_CONDITIONS = "default"
    private static final Map<String, String> OPENX_CONFIG = ["adapters.${OPENX}.enabled" : "true",
                                                             "adapters.${OPENX}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    private static final Map<String, String> AMX_CONFIG = ["adapters.${AMX}.enabled" : "true",
                                                           "adapters.${AMX}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    private static final Map<String, String> OPENX_ALIAS_CONFIG = ["adapters.${OPENX}.aliases.${OPENX_ALIAS}.enabled" : "true",
                                                                   "adapters.${OPENX}.aliases.${OPENX_ALIAS}.endpoint": "$networkServiceContainer.rootUri/auction".toString()]
    private static final String PB_RULE_ENGINE_CODE = "pb-rule-engine"
    private static final String CONFIG_DATA_CENTER = PBSUtils.randomString
    private static final PrebidServerService pbsServiceWithRulesEngineModule = pbsServiceFactory.getService(
            getRulesEngineSettings() + AMX_CONFIG + OPENX_CONFIG + OPENX_ALIAS_CONFIG + ['datacenter-region': CONFIG_DATA_CENTER])

    def "PBS shouldn't remove bidder when rule engine not fully configured in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with enabled rules engine"
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
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED
        assert bidResponse.seatbid.seat.sort() == [GENERIC, OPENX, AMX].sort()

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        where:
        pbRulesEngine << [
                createRulesEngineWithRule().tap { it.ruleSets = [] },
                createRulesEngineWithRule().tap { it.ruleSets[0].stage = null },
                createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].schema = [] },
                createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].rules = [] },
                createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].rules[0].results = [] },
                createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].rules[0].conditions = [] },
        ]
    }

    def "PBS shouldn't remove bidder and emit a warning when args rule engine not fully configured in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with enabled rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.getAccountId(),
                createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups[0].rules[0].results[0].args = null })
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
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED
        assert bidResponse.seatbid.seat.sort() == [GENERIC, OPENX, AMX].sort()

        and: "Invocation result should contain warning of rule engine"
        assert getInvocationResult(bidResponse)[0].message == "Rule for account ${bidRequest.accountId} is not ready"
    }

    def "PBS shouldn't remove bidder and emit a warning when model group rule engine not fully configured in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with enabled rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.getAccountId(),
                createRulesEngineWithRule().tap { it.ruleSets[0].modelGroups = [] })
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
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED
        assert bidResponse.seatbid.seat.sort() == [GENERIC, OPENX, AMX].sort()

        and: "Invocation result should contain warning of rule engine"
        assert getInvocationResult(bidResponse)[0].message == "Rule for account ${bidRequest.accountId} is not ready"
    }

    def "PBS shouldn't log default model when rule does not fired and empty model default"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.device = new Device(geo: new Geo(country: BULGARIA))
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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

    def "PBS shouldn't remove bidder when rule engine disabled or absent in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with disabled or without rules engine "
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        where:
        pbRuleEngine << [null, createRulesEngineWithRule(false)]
    }

    def "PBS shouldn't remove bidder when rule sets disabled in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "Analytics result shouldn't contain info about module exclude"
        assert !getAnalyticResults(bidResponse)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid
    }

    def "PBS shouldn't remove any bidder without cache request"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule()
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "Analytics result shouldn't contain info about module exclude"
        assert !getAnalyticResults(bidResponse)
    }

    def "PBS should remove bidder and not update analytics when bidder matched with conditions and without analytics key"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets without analytics value"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].analyticsKey = null
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain two seat"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1

        and: "Response should contain seat bid"
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Analytics result shouldn't contain info about module exclude"
        assert !getAnalyticResults(bidResponse)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should remove bidder from imps and use default 203 value for seatNonBid when seatNonBid null and exclude bidder in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                openx = Openx.defaultOpenx
                amx = new Amx()
            }
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC)]
            it.ruleSets[0].modelGroups[0].rules[0].results[0].args.seatNonBid = null
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == [OPENX, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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

        and: "Response should populate seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should remove bidder from imps and update seatNonBid with other code when seatNonBid override and exclude bidder in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                openx = Openx.defaultOpenx
                amx = new Amx()
            }
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def bidRejectionReason = PBSUtils.getRandomEnum(BidRejectionReason)
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC)]
            it.ruleSets[0].modelGroups[0].rules[0].results[0].args.seatNonBid = bidRejectionReason
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == [OPENX, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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

        and: "Response should populate seatNon bid"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == bidRejectionReason
    }

    def "PBS should remove bidder from imps and not update seatNonBid when returnAllBidStatus disabled and exclude bidder in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                openx = Openx.defaultOpenx
                amx = new Amx()
            }
            updateBidRequestWithGeoCountry(it)
            ext.prebid.tap {
                returnAllBidStatus = false
                trace = VERBOSE
            }
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC)]
            it.ruleSets[0].modelGroups[0].rules[0].results[0].args.seatNonBid = ERROR_NO_BID
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.seat.sort() == [OPENX, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Response shouldn't populate seatNon bid with code 203"
        assert !bidResponse.ext.seatnonbid

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
    }

    def "PBS should when bidder doesn't specified with account and request"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [//createRuleEngineModelRuleWithExcludeResult(UNKNOWN),
                                                              createRuleEngineModelRuleWithIncludeResult(UNKNOWN)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == 0

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid
    }

    def "PBS should include one bidder and update analytics when multiple bidders specified and one included in account"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(OPENX)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == ONE_BIDDER_REQUESTED

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved.sort() == [GENERIC, AMX].sort()
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == bidRequest.imp.id

        and: "Response should populate seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should remove bidder by device geo from imps when bidder excluded in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(defaultImpression)
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == 2

        and: "Bid response should contain seatBids"
        assert bidResponse.seatbid.seat.sort() == [OPENX, AMX].sort()

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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

        and: "Response should populate seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should leave only include bidder at imps when bidder include in account config"() {
        given: "Bid request with multiply imps bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                openx = Openx.defaultOpenx
                amx = new Amx()
            }
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithIncludeResult(GENERIC)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == ONE_BIDDER_REQUESTED
        assert bidResponse.seatbid.seat == [GENERIC]

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved == [AMX, OPENX]
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == bidRequest.imp.id

        and: "Response should populate seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should only logATag when present only function log a tag"() {
        given: "Bid request with multiply imps bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                openx = Openx.defaultOpenx
                amx = new Amx()
            }
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithLogATagResult()]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == 3

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert !impResult.values.biddersRemoved
        assert !impResult.values.seatNonBid
        assert impResult.appliedTo.impIds == [APPLIED_FOR_ALL_IMPS]
    }

    def "PBS should leave only hard alias bidder at imps when hard alias bidder include in account config"() {
        given: "Bid request with multiply imps bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                openx = Openx.defaultOpenx
                amx = new Amx()
                openxAlias = Openx.defaultOpenx
            }
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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
        assert bidResponse.seatbid.size() == ONE_BIDDER_REQUESTED

        and: "Bid response should contain seatBid.seat"
        assert bidResponse.seatbid.seat == [OPENX_ALIAS]

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved.sort() == [GENERIC, OPENX, AMX]
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == bidRequest.imp.id

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
            it.imp.add(defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                openx = Openx.defaultOpenx
                amx = new Amx()
                openxAlias = Openx.defaultOpenx
            }
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED
        assert bidResponse.seatbid.seat.sort() == [OPENX, AMX, GENERIC].sort()

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert impResult.appliedTo.impIds == [bidRequest.imp[1].id]

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
            it.imp.add(defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                alias = new Generic()
                generic = null
                amx = new Amx()
                openx = Openx.defaultOpenx
            }
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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
        assert bidResponse.seatbid.size() == ONE_BIDDER_REQUESTED
        assert bidResponse.seatbid.seat == [ALIAS]

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved.sort() == [OPENX, GENERIC, AMX].sort()
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == bidRequest.imp.id

        and: "Response should seatNon bid with code 203"
        def seatNonBid = bidResponse.ext.seatnonbid
        assert seatNonBid.seat == [ALIAS]
        assert seatNonBid.nonBid[0].impId == [bidRequest.imp[0].id]
        assert seatNonBid.nonBid[0].statusCode == [REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE]
    }

    def "PBS should remove soft alias bidder from imps when soft alias bidder excluded in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.imp.add(Imp.defaultImpression)
            it.imp[1].ext.prebid.bidder.tap {
                alias = new Generic()
                generic = null
                amx = new Amx()
                openx = Openx.defaultOpenx
            }
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED
        assert bidResponse.seatbid.seat.sort() == [OPENX, AMX, GENERIC].sort()

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved == [ALIAS]
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == [bidRequest.imp[1].id]

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == ALIAS
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should remove bidder and update analytics when first rule sets disabled and second enabled in account config"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets.first.enabled = false
            it.ruleSets.add(RuleSets.createRuleSets())
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain two seat"
        assert bidResponse.seatbid.size() == 2

        and: "Response should contain seat bid"
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[1].modelGroups[0]
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

    def "PBS should skip rule set and take next one when rule sets not a processed auction request"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rules engine and several rule sets"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC),
                                                              createRuleEngineModelRuleWithExcludeResult(AMX),
                                                              createRuleEngineModelRuleWithExcludeResult(OPENX)]
            it.ruleSets[0].stage = stage as Stage
            it.ruleSets.add(RuleSets.createRuleSets())
            it.ruleSets[1].modelGroups[0].rules[0].results = [createRuleEngineModelRuleWithExcludeResult(AMX)]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain two seat"
        assert bidResponse.seatbid.size() == 2

        and: "Response should contain seat bid"
        assert bidResponse.seatbid.seat.sort() == [GENERIC, OPENX].sort()

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[1].modelGroups[0]
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

        where:
        stage << Stage.values() - Stage.PROCESSED_AUCTION_REQUEST
    }

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
        assert result.name == PB_RULE_ENGINE_CODE
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

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved.sort() == [AMX, OPENX, GENERIC].sort()
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == bidRequest.imp.id

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

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved.sort() == [OPENX, AMX]
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == bidRequest.imp.id

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

    def "PBS shouldn't take rule with higher weight and remove bidder when weight negative or zero"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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

        where:
        weight << [PBSUtils.randomNegativeNumber, 0]
    }

    def "PBS should take rule with higher weight and remove bidder when two model group with different weight"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with few model group"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                it.weight = 1
                it.rules[0].results = [createRuleEngineModelRuleWithIncludeResult(GENERIC)]
            }
            it.ruleSets[0].modelGroups.add(RulesEngineModelGroups.createRulesModuleGroup())
            it.ruleSets[0].modelGroups[1].tap {
                it.weight = 100
                it.rules[0].results = [createRuleEngineModelRuleWithExcludeResult(GENERIC)]
            }
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

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
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[1]
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

    def "PBS shouldn't log the default model group and should modify response when other rule fire"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with default model"
        def analyticsValue = PBSUtils.randomString
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].modelDefault = [new RuleEngineModelDefault(
                    function: LOG_A_TAG,
                    args: new RuleEngineModelDefaultArgs(analyticsValue: analyticsValue))]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
    }

    def "PBS should log the default model group and shouldn't modify response when other rules not fire"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            it.device = new Device(geo: new Geo(country: BULGARIA))
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with default model"
        def analyticsValue = PBSUtils.randomString
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].modelDefault = [new RuleEngineModelDefault(
                    function: LOG_A_TAG,
                    args: new RuleEngineModelDefaultArgs(analyticsValue: analyticsValue))]
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

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == analyticsValue
        assert impResult.values.resultFunction == LOG_A_TAG.value
        assert impResult.values.conditionFired == DEFAULT_CONDITIONS
        assert impResult.appliedTo.impIds == [APPLIED_FOR_ALL_IMPS]

        assert !impResult.values.biddersRemoved
        assert !impResult.values.seatNonBid
    }

    def "PBS shouldn't log the default model group and modify response when rules fire"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with default model"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].modelDefault = [new RuleEngineModelDefault(
                    function: LOG_A_TAG,
                    args: new RuleEngineModelDefaultArgs(analyticsValue: PBSUtils.randomString))]
        }
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain two seat"
        assert bidResponse.seatbid.size() == 2

        and: "Response should contain seat bid"
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS should reject processing rule engine when #function schema function contain args"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Failed to parse rule-engine config for account ${bidRequest.accountId}: " +
                "Function '${function.value}' configuration is invalid: No arguments allowed")

        where:
        function << [DEVICE_TYPE, AD_UNIT_CODE, BUNDLE, DOMAIN, TCF_IN_SCOPE, GPP_SID_AVAILABLE, FPD_AVAILABLE,
                     USER_FPD_AVAILABLE, EID_AVAILABLE, CHANNEL, DEVICE_COUNTRY]
    }

    def "PBS should exclude bidder when deviceCountry match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: DEVICE_COUNTRY)]
                rules[0].conditions = [USA.toString()]
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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

    def "PBS shouldn't exclude bidder when deviceCountry not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema[0].function = DEVICE_COUNTRY
                rules[0].conditions = [PBSUtils.getRandomEnum(Country, [USA]).toString()]
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Account cache"
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

    def "PBS should reject processing rule engine when dataCenterIn schema function args contain invalid data"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DATA_CENTER_IN
                it.args = new RuleEngineFunctionArgs(countries: [CONFIG_DATA_CENTER])
            }
        }

        and: "Account with disabled or without rules engine"
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

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Function 'dataCenterIn' configuration is invalid: " +
                "Field 'datacenters' is required and has to be an array of strings")
    }

    def "PBS should exclude bidder when dataCenterIn match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DATA_CENTER_IN
                it.args = new RuleEngineFunctionArgs(datacenters: [CONFIG_DATA_CENTER])
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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

    def "PBS shouldn't exclude bidder when dataCentersIn not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DATA_CENTER_IN
                it.args = new RuleEngineFunctionArgs(datacenters: [PBSUtils.randomString])
            }
        }

        and: "Account with disabled or without rules engine"
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

    def "PBS should exclude bidder when dataCenter match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: DATA_CENTER)]
                rules[0].conditions = [CONFIG_DATA_CENTER]
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when dataCenter not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: DATA_CENTER)]
                rules[0].conditions = [PBSUtils.randomString]
            }
        }

        and: "Account with disabled or without rules engine"
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

    def "PBS should exclude bidder when channel match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: CHANNEL)]
                rules[0].conditions = [WEB.value]
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when channel not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: CHANNEL)]
                rules[0].conditions = [PBSUtils.getRandomEnum(ChannelType, [WEB]).value]
            }
        }

        and: "Account with disabled or without rules engine"
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

    def "PBS should exclude bidder when eidAvailable match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            user = new User(eids: [Eid.getDefaultEid()])
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: EID_AVAILABLE)]
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when eidAvailable not match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            user = new User(eids: eids)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema[0].function = EID_AVAILABLE
                rules[0].conditions = [TRUE as String]
            }
        }

        and: "Account with disabled or without rules engine"
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

        where:
        eids << [null, []]
    }

    def "PBS should reject processing rule engine when eidIn schema function args contain invalid data"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithGeoCountry(it)
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = EID_IN
                it.args = new RuleEngineFunctionArgs(sources: [PBSUtils.randomNumber])
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about module exclude"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Function '${EID_IN}' configuration is invalid: " +
                "Field 'sources' is required and has to be an array of strings")
    }

    def "PBS should exclude bidder when eidIn match with condition"() {
        given: "Bid request with multiply bidders"
        def eid = Eid.getDefaultEid()
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            user = new User(eids: [eid])
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = EID_IN
                it.args = new RuleEngineFunctionArgs(sources: [PBSUtils.randomString, eid.source, PBSUtils.randomString])
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when eidIn match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            user = new User(eids: [Eid.getDefaultEid()])
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = EID_IN
                it.args = new RuleEngineFunctionArgs(sources: [PBSUtils.randomString, PBSUtils.randomString])
            }
        }

        and: "Account with disabled or without rules engine"
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

    def "PBS should exclude bidder when userFpdAvailable match with condition"() {
        given: "Bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            user = requestedUfpUser
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: USER_FPD_AVAILABLE)]
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            user = requestedUfpUser
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: USER_FPD_AVAILABLE)]
        }

        and: "Account with disabled or without rules engine"
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

        where:
        requestedUfpUser << [new User(data: null), new User(data: [null]), //todo  empty
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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

        where:
        bidRequest << [
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    user = new User(data: [Data.defaultData])
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    user = new User(ext: new UserExt(data: UserExtData.FPDUserExtData))
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    site.content = new Content(data: [Data.defaultData])
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    site.ext = new SiteExt(data: SiteExtData.FPDSiteExtData)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    app.content = new Content(data: [Data.defaultData])
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    app.ext = new AppExt(data: new AppExtData(language: PBSUtils.randomString))
                },
        ]
    }

    def "PBS shouldn't exclude bidder when fpdAvailable not match with condition"() {
        given: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: FPD_AVAILABLE)]
        }

        and: "Account with disabled or without rules engine"
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

        where:
        bidRequest << [
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    user = new User(data: null, ext: new UserExt(data: null))
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    user = new User(ext: new UserExt(data: null))
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    site.content = new Content(data: [null])                     //todo PLEASE TAKE A LOOK
                    site.ext = new SiteExt(data: null)
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    site.ext = new SiteExt(data: null)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    app.content = new Content(data: [null])                         //todo PLEASE TAKE A LOOK
                    app.ext = new AppExt(data: null)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    app.ext = new AppExt(data: null)
                },
        ]
    }

    def "PBS should exclude bidder when gppSidAvailable match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when gppSidAvailable not match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            regs = new Regs(gppSid: gppSid)
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema = [new RuleEngineModelSchema(function: GPP_SID_AVAILABLE)]
        }

        and: "Account with disabled or without rules engine"
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

        where:
        gppSid << [[PBSUtils.randomNegativeNumber], null]
    }

    def "PBS should reject processing rule engine when gppSidIn schema function args contain invalid data"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            regs = new Regs(gdpr: 0, gppSid: [PBSUtils.getRandomEnum(GppSectionId, [GppSectionId.TCF_EU_V2]).getIntValue()])
        }

        and: "Account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = GPP_SID_IN
                it.args = new RuleEngineFunctionArgs(sids: [PBSUtils.randomString])
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Failed to parse rule-engine config for account ${bidRequest.accountId}: " +
                "Function '${GPP_SID_IN}' configuration is invalid: " +
                "Field 'sids' is required and has to be an array of integers").size() == 1
    }

    def "PBS should exclude bidder when gppSidIn match with condition"() {
        given: "Default bid request with multiply bidder"
        def gppSectionId = PBSUtils.getRandomEnum(GppSectionId).getIntValue()
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            regs = new Regs(gppSid: [gppSectionId])
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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved == groups.rules.first.results.first.args.bidders
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == bidRequest.imp.id

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when gppSidIn not match with condition"() {
        given: "Default bid request with multiply bidder"
        def gppSectionId = PBSUtils.getRandomEnum(GppSectionId)
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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

    def "PBS should exclude bidder when tcfInScope match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
        assert result.status == SUCCESS
        def impResult = result.results[0]
        def groups = pbRuleEngine.ruleSets[0].modelGroups[0]
        assert impResult.status == SUCCESS
        assert impResult.values.analyticsKey == groups.analyticsKey
        assert impResult.values.modelVersion == groups.version
        assert impResult.values.analyticsValue == groups.rules.first.results.first.args.analyticsValue
        assert impResult.values.resultFunction == groups.rules.first.results.first.function.value
        assert impResult.values.conditionFired == groups.rules.first.conditions.first
        assert impResult.values.biddersRemoved == groups.rules.first.results.first.args.bidders
        assert impResult.values.seatNonBid == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
        assert impResult.appliedTo.impIds == bidRequest.imp.id

        and: "Response should seatNon bid with code 203"
        assert bidResponse.ext.seatnonbid.size() == 1
        def seatNonBid = bidResponse.ext.seatnonbid[0]
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

        where:
        gdpr | condition
        1    | TRUE as String
        0    | FALSE as String
    }

    def "PBS shouldn't exclude bidder when tcfInScope not match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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

        and: "Account cache"
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

        where:
        gdpr | condition
        0    | TRUE as String
        1    | FALSE as String
    }

    def "PBS should reject processing rule engine when percent schema function args contain invalid data"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = PERCENT
                it.args = new RuleEngineFunctionArgs(pct: PBSUtils.randomString)
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "Analytics result should contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Function 'percent' configuration is invalid: " +
                "Field 'pct' is required and has to be an integer")
    }

    def "PBS should exclude bidder when percent match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Create rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = PERCENT
                it.args = new RuleEngineFunctionArgs(pct: percent as Integer)
            }
        }

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

        where:
        percent << [100, PBSUtils.getRandomNumber(100)]
    }

    def "PBS shouldn't exclude bidder when percent not match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = PERCENT
                it.args = new RuleEngineFunctionArgs(pct: percent)
            }
        }

        and: "Save account with disabled or without rules engine"
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

        where:
        percent << [0, PBSUtils.randomNegativeNumber]
    }

    def "PBS should reject processing the rule engine when the prebidKey schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = PREBID_KEY
                it.args = new RuleEngineFunctionArgs(key: PBSUtils.randomNumber)
            }
        }

        and: "Account with disabled or without rules engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "PBS response should not contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Function 'prebidKey' configuration is invalid: " +
                "Field 'key' is required and has to be a string")
    }

    def "PBS should exclude bidder when prebidKey match with condition"() {
        given: "Default bid request with multiply bidder"
        def key = PBSUtils.randomString
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            ext.prebid.kvps = new Kvps(anyString: key)
        }

        and: "Create rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = PREBID_KEY
                it.args = new RuleEngineFunctionArgs(key: "anyString")
            }
            it.ruleSets[0].modelGroups[0].rules[0].conditions = [key]
        }

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when prebidKey not match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            ext.prebid.kvps = new Kvps(anyString: PBSUtils.randomString)
        }

        and: "Create rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = PREBID_KEY
                it.args = new RuleEngineFunctionArgs(key: "anyString")
            }
            it.ruleSets[0].modelGroups[0].rules[0].conditions = [PBSUtils.randomString]
        }

        and: "Save account with disabled or without rules engine"
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

    def "PBS should exclude bidder when domain match with condition"() {
        given: "Default bid request with multiply bidder"
        def randomDomain = PBSUtils.randomString
        def bidRequest = bidRequestClosure(randomDomain)

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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

        where:
        distributionChannel | bidRequestClosure
        SITE                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.site.publisher = new Publisher(id: PBSUtils.randomString, domain: domain)
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
                updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            }
        }
        APP                 | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.app.publisher = new Publisher(id: PBSUtils.randomString, domain: domain)
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
                updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            }
        }
        DOOH                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.dooh.publisher = new Publisher(id: PBSUtils.randomString, domain: domain)
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
                updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            }
        }
    }

    def "PBS shouldn't exclude bidder when domain not match with condition"() {
        given: "Create account with rule engine config"
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

        where:
        bidRequest << [
                getDefaultBidRequestWithMultiplyBidders().tap {
                    it.site.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    it.app.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(DOOH).tap {
                    it.dooh.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                }]

    }

    def "PBS should reject processing the rule engine when the domainIn schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiplyB bidders"
        def randomDomain = PBSUtils.randomString
        def bidRequest = bidRequestClosure(randomDomain)

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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Function 'domainIn' configuration is invalid: " +
                "Field 'domains' is required and has to be an array of strings")

        where:
        distributionChannel | bidRequestClosure
        SITE                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.site.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
            }
        }
        SITE                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.site.domain = domain
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
            }
        }
        APP                 | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.app.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
            }
        }
        APP                 | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.app.domain = domain
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
            }
        }
        DOOH                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.dooh.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
            }
        }
        DOOH                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.dooh.domain = domain
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
            }
        }
    }

    def "PBS should exclude bidder when domainIn match with condition"() {
        given: "Default bid request with multiply bidder"
        def randomDomain = PBSUtils.randomString
        def bidRequest = bidRequestClosure(randomDomain)

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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

        where:
        distributionChannel | bidRequestClosure
        SITE                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.site.publisher = new Publisher(id: PBSUtils.randomString, domain: domain)
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
                updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)

            }
        }
        SITE                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.site.domain = domain
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
                updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)

            }
        }
        APP                 | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.app.publisher = new Publisher(id: PBSUtils.randomString, domain: domain)
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
                updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)

            }
        }
        APP                 | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.app.domain = domain
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
                updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)

            }
        }
        DOOH                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.dooh.publisher = new Publisher(id: PBSUtils.randomString, domain: domain)
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
                updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)

            }
        }
        DOOH                | { String domain ->
            BidRequest.getDefaultBidRequest(distributionChannel).tap {
                it.dooh.domain = domain
                it.imp[0].ext.prebid.bidder.amx = new Amx()
                it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
                it.imp[0].ext.prebid.bidder.generic = new Generic()
                updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            }
        }
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

        where:
        bidRequest << [
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    it.site.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders().tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    it.site.domain = PBSUtils.randomString
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    it.app.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(APP).tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    it.app.domain = PBSUtils.randomString
                },
                getDefaultBidRequestWithMultiplyBidders(DOOH).tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    it.dooh.publisher = new Publisher(id: PBSUtils.randomString, domain: PBSUtils.randomString)
                },
                getDefaultBidRequestWithMultiplyBidders(DOOH).tap {
                    updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                    it.dooh.domain = PBSUtils.randomString
                }]

    }

    def "PBS should exclude bidder when bundle match with condition"() {
        given: "Default bid request with multiply bidder"
        def bundle = PBSUtils.randomString
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when bundle not match with condition"() {
        given: "Default bid request with multiply bidder"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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

    def "PBS should reject processing the rule engine when the bundleIn schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        then: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Failed to parse rule-engine config for account ${bidRequest.accountId}: " +
                "Function '${BUNDLE_IN}' configuration is invalid: Field 'bundles' is required and has to be an array of strings").size() == 1
    }

    def "PBS should exclude bidder when bundleIn match with condition"() {
        given: "Default bid request with multiply bidders"
        def bundle = PBSUtils.randomString
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when bundleIn not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders(APP).tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
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

    def "PBS should reject processing the rule engine when the mediaTypeIn schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

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

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        then: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Failed to parse rule-engine config for account ${bidRequest.accountId}: " +
                "Function '${MEDIA_TYPE_IN}' configuration is invalid: Field 'types' is required and has to be an array of strings").size() == 1

        where:
        mediaTypeInArgs << [null, PBSUtils.randomNumber]
    }

    def "PBS should exclude bidder when mediaTypeIn match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

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

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when mediaTypeIn not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

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

    def "PBS should exclude bidder when adUnitCode match with condition"() {
        given: "Default bid request with multiply bidders"
        def adUnitCode = PBSUtils.randomString
        def bidRequest = bidRequestClosure(adUnitCode)

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: AD_UNIT_CODE)]
                rules[0].conditions = [adUnitCode]
            }
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest)
        storedImpDao.save(storedImp)

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

        where:
        bidRequestClosure << [
                { tagId ->
                    getDefaultBidRequestWithMultiplyBidders().tap {
                        updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                        imp[0].tagId = tagId
                    }
                },
                { gpid ->
                    getDefaultBidRequestWithMultiplyBidders().tap {
                        updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                        imp[0].ext.gpid = gpid
                    }
                },
                { pbAdSlot ->
                    getDefaultBidRequestWithMultiplyBidders().tap {
                        updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                        imp[0].ext.data = new ImpExtContextData(pbAdSlot: pbAdSlot)
                    }
                },
                { storedRequestId ->
                    getDefaultBidRequestWithMultiplyBidders().tap {
                        updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                        imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                    }
                }
        ]
    }

    def "PBS shouldn't exclude bidder when adUnitCode not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

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

    def "PBS should reject processing the rule engine when the adUnitCodeIn schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            imp[0].tagId = PBSUtils.randomString
            imp[0].ext.gpid = PBSUtils.randomString
            imp[0].ext.data = new ImpExtContextData(pbAdSlot: PBSUtils.randomString)
            imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impData = getDefaultImpression()
        }
        storedImpDao.save(storedImp)

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = AD_UNIT_CODE
                it.args = new RuleEngineFunctionArgs(codes: [arguments])
            }
        }

        and: "Save account with rule engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Failed to parse rule-engine config for account ${bidRequest.accountId}: " +
                "Function '${AD_UNIT_CODE}' configuration is invalid: No arguments allowed").size() == 1

        where:
        arguments << [PBSUtils.randomBoolean, PBSUtils.randomNumber]
    }

    def "PBS should exclude bidder when adUnitCodeIn match with condition"() {
        given: "Default bid request with multiply bidders"
        def randomString = PBSUtils.randomString
        def bidRequest = bidRequestClosure(randomString)

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = AD_UNIT_CODE_IN
                it.args = new RuleEngineFunctionArgs(codes: [PBSUtils.randomString, randomString])
            }
        }

        and: "Save storedImp into DB"
        def storedImp = StoredImp.getStoredImp(bidRequest)
        storedImpDao.save(storedImp)

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE

        where:
        bidRequestClosure << [
                { storedRequestId ->
                    getDefaultBidRequestWithMultiplyBidders().tap {
                        updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                        imp[0].tagId = PBSUtils.randomString
                        imp[0].ext.gpid = PBSUtils.randomString
                        imp[0].ext.data = new ImpExtContextData(pbAdSlot: PBSUtils.randomString)
                        imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                    }
                },
                { pbAdSlot ->
                    getDefaultBidRequestWithMultiplyBidders().tap {
                        updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                        imp[0].tagId = PBSUtils.randomString
                        imp[0].ext.gpid = PBSUtils.randomString
                        imp[0].ext.data = new ImpExtContextData(pbAdSlot: pbAdSlot)
                        imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
                    }
                },
                { gpid ->
                    getDefaultBidRequestWithMultiplyBidders().tap {
                        updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                        imp[0].tagId = PBSUtils.randomString
                        imp[0].ext.gpid = gpid
                        imp[0].ext.data = new ImpExtContextData(pbAdSlot: PBSUtils.randomString)
                        imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
                    }
                },
                { tagId ->
                    getDefaultBidRequestWithMultiplyBidders().tap {
                        updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
                        imp[0].tagId = tagId
                        imp[0].ext.gpid = PBSUtils.randomString
                        imp[0].ext.data = new ImpExtContextData(pbAdSlot: PBSUtils.randomString)
                        imp[0].ext.prebid.storedRequest = new PrebidStoredRequest(id: PBSUtils.randomString)
                    }
                }
        ]
    }

    def "PBS shouldn't exclude bidder when adUnitCodeIn not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
        }

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

    def "PBS should exclude bidder when deviceType match with condition"() {
        given: "Default bid request with multiply bidders"
        def deviceType = PBSUtils.getRandomEnum(DeviceType)
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            device = new Device(devicetype: deviceType)
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: DEVICE_TYPE)]
                rules[0].conditions = [deviceType.value as String]
            }
        }

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when deviceType not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            device = new Device(devicetype: PBSUtils.getRandomEnum(DeviceType))
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].tap {
                schema = [new RuleEngineModelSchema(function: DEVICE_TYPE)]
                rules[0].conditions = [PBSUtils.getRandomEnum(DeviceType).value as String]
            }
        }

        and: "Save account with disabled or without rules engine"
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

    def "PBS should reject processing the rule engine when the deviceTypeIn schema function contains incompatible arguments"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            device = new Device(devicetype: PBSUtils.getRandomEnum(DeviceType))
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DEVICE_COUNTRY_IN
                it.args = new RuleEngineFunctionArgs(types: [PBSUtils.randomString])
            }
        }

        and: "Save account with rule engine"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "PBs should perform bidder request"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "Bid response should contain all requested bidders"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED

        and: "PBS response shouldn't contain seatNonBid"
        assert !bidResponse.ext.seatnonbid

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result shouldn't contain info about rule engine"
        assert !getAnalyticResults(bidResponse)

        and: "Logs should contain error"
        def logs = pbsServiceWithRulesEngineModule.getLogsByTime(startTime)
        assert getLogsByText(logs, "Function '${DEVICE_COUNTRY_IN}' configuration is invalid: " +
                "Field 'countries' is required and has to be an array of strings")
    }

    def "PBS should exclude bidder when deviceTypeIn match with condition"() {
        given: "Default bid request with multiply bidders"
        def deviceType = PBSUtils.getRandomEnum(DeviceType)
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            device = new Device(devicetype: deviceType)
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DEVICE_TYPE_IN
                it.args = new RuleEngineFunctionArgs(types: [deviceType.value])
            }
        }

        and: "Save account with rule engine config"
        def accountWithRulesEngine = getAccountWithRulesEngine(bidRequest.accountId, pbRuleEngine)
        accountDao.save(accountWithRulesEngine)

        and: "Cache account"
        pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithRulesEngineModule.sendAuctionRequest(bidRequest)

        then: "Bid response should contain seats"
        assert bidResponse.seatbid.size() == BIDDERS_REQUESTED - 1
        assert bidResponse.seatbid.seat.sort() == [GENERIC, AMX].sort()

        and: "PBs should perform bidder requests"
        assert bidder.getBidderRequests(bidRequest.id)

        and: "PBS should not contain errors, warnings"
        assert !bidResponse.ext?.warnings
        assert !bidResponse.ext?.errors

        and: "Analytics result should contain info about module exclude"
        def analyticsResult = getAnalyticResults(bidResponse)
        def result = analyticsResult[0]
        assert result.name == PB_RULE_ENGINE_CODE
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
        assert seatNonBid.seat == OPENX
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REQUEST_BIDDER_REMOVED_BY_RULE_ENGINE_MODULE
    }

    def "PBS shouldn't exclude bidder when deviceTypeIn not match with condition"() {
        given: "Default bid request with multiply bidders"
        def bidRequest = getDefaultBidRequestWithMultiplyBidders().tap {
            updateBidRequestWithTraceVerboseAndReturnAllBidStatus(it)
            device = new Device(devicetype: PBSUtils.getRandomEnum(DeviceType))
        }

        and: "Create account with rule engine config"
        def pbRuleEngine = createRulesEngineWithRule().tap {
            it.ruleSets[0].modelGroups[0].schema[0].tap {
                it.function = DEVICE_TYPE_IN
                it.args = new RuleEngineFunctionArgs(types: [PBSUtils.getRandomEnum(DeviceType).value as String])
            }
        }

        and: "Save account with disabled or without rules engine"
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

    private static BidRequest getDefaultBidRequestWithMultiplyBidders(DistributionChannel distributionChannel = SITE) {
        BidRequest.getDefaultBidRequest(distributionChannel).tap {
            it.imp[0].ext.prebid.bidder.amx = new Amx()
            it.imp[0].ext.prebid.bidder.openx = Openx.defaultOpenx
            it.imp[0].ext.prebid.bidder.generic = new Generic()
        }
    }

    private static void updateBidRequestWithGeoCountry(BidRequest bidRequest) {
        bidRequest.device = new Device(geo: new Geo(country: USA))
    }

    private static void updateBidRequestWithTraceVerboseAndReturnAllBidStatus(BidRequest bidRequest) {
        bidRequest.ext.prebid.tap {
            it.trace = VERBOSE
            it.returnAllBidStatus = true
        }
    }

    private static getAccountWithRulesEngine(String accountId, PbRulesEngine ruleEngine) {
        def accountHooksConfiguration = new AccountHooksConfiguration(modules: new PbsModulesConfig(pbRuleEngine: ruleEngine))
        new Account(uuid: accountId, config: new AccountConfig(hooks: accountHooksConfiguration))
    }
}
