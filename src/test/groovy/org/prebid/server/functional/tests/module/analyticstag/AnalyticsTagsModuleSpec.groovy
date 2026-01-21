package org.prebid.server.functional.tests.module.analyticstag

import org.prebid.server.functional.model.config.AccountAnalyticsConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.AnalyticsOptions
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.FetchStatus
import org.prebid.server.functional.model.request.auction.PrebidAnalytics
import org.prebid.server.functional.model.request.auction.RichmediaFilter
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.response.auction.AnalyticTagStatus
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ModuleActivityName
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.config.ModuleName.ORTB2_BLOCKING
import static org.prebid.server.functional.model.config.ModuleName.PB_RICHMEDIA_FILTER
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.model.config.Stage.ALL_PROCESSED_BID_RESPONSES
import static org.prebid.server.functional.model.config.Stage.RAW_BIDDER_RESPONSE
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class AnalyticsTagsModuleSpec extends ModuleBaseSpec {

    def "PBS should include analytics tag for ortb2-blocking module in response when request and account allow client details"() {
        given: "Default account with module config"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.analytics = new PrebidAnalytics(options: new AnalyticsOptions(enableClientDetails: true))
        }

        and: "Account in the DB"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [RAW_BIDDER_RESPONSE])
        def hooksConfiguration = new AccountHooksConfiguration(executionPlan: executionPlan)
        def accountConfig = new AccountConfig(hooks: hooksConfiguration, analytics: new AccountAnalyticsConfig(allowClientDetails: true))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "Bid response should contain ext.prebid.analyticsTags with module record"
        def analyticsTagPrebid = bidResponse.ext.prebid.analytics.tags.first
        assert analyticsTagPrebid.stage == RAW_BIDDER_RESPONSE.value
        assert analyticsTagPrebid.module == ORTB2_BLOCKING.code

        and: "Analytics tag should contain results with name and success status"
        def analyticResult = analyticsTagPrebid.analyticsTags.activities.first
        assert analyticResult.status == AnalyticTagStatus.SUCCESS
        assert analyticResult.name == ModuleActivityName.ORTB2_BLOCKING

        and: "Should include appliedTo information in analytics tags results"
        verifyAll(analyticResult.results.first) {
            it.status == AnalyticTagStatus.SUCCESS_ALLOW
            it.appliedTo.bidders == [GENERIC.value]
            it.appliedTo.impIds == bidRequest.imp.id
        }
    }

    def "PBS should include analytics tag for richmedia module in response when request and account allow client details"() {
        given: "PBS server with enabled media filter"
        def PATTERN_NAME = PBSUtils.randomString
        def pbsServiceWithEnabledMediaFilter = pbsServiceFactory.getService(getRichMediaFilterSettings(PATTERN_NAME))

        and: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.trace = VERBOSE
            it.ext.prebid.analytics = new PrebidAnalytics(options: new AnalyticsOptions(enableClientDetails: true))
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = PATTERN_NAME
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB with cofig"
        def richmediaFilter = new RichmediaFilter(filterMraid: true, mraidScriptPattern: PATTERN_NAME)
        def richMediaFilterConfig = new PbsModulesConfig(pbRichmediaFilter: richmediaFilter)
        def accountHooksConfig = new AccountHooksConfiguration(modules: richMediaFilterConfig)
        def accountAnalyticsConfig = new AccountAnalyticsConfig(allowClientDetails: true)
        def accountConfig = new AccountConfig(hooks: accountHooksConfig, analytics: accountAnalyticsConfig)
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithEnabledMediaFilter.sendAuctionRequest(bidRequest)

        then: "Bid response should contain ext.prebid.analyticsTags with module record"
        def analyticsTagPrebid = bidResponse.ext.prebid.analytics.tags.first
        assert analyticsTagPrebid.stage == ALL_PROCESSED_BID_RESPONSES.value
        assert analyticsTagPrebid.module == PB_RICHMEDIA_FILTER.code

        and: "Analytics tag should contain results with name and success status"
        def analyticResult = analyticsTagPrebid.analyticsTags.activities.first
        assert analyticResult.status == AnalyticTagStatus.SUCCESS
        assert analyticResult.name == ModuleActivityName.REJECT_RICHMEDIA

        and: "Should include appliedTo information in analytics tags results"
        verifyAll(analyticResult.results.first) {
            it.status == AnalyticTagStatus.SUCCESS_BLOCK
            it.appliedTo.bidders == [GENERIC.value]
            it.appliedTo.impIds == bidRequest.imp.id
        }

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(getRichMediaFilterSettings(PATTERN_NAME))
    }

    def "PBS should include analytics tag in response when request and default account allow client details"() {
        given: "Default account with module config"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [RAW_BIDDER_RESPONSE])
        def hooksConfiguration = new AccountHooksConfiguration(executionPlan: executionPlan)
        def accountConfig = new AccountConfig(hooks: hooksConfiguration, analytics: new AccountAnalyticsConfig(allowClientDetails: true))

        and: "Prebid server with proper default account"
        def pbsConfig = ['settings.default-account-config': encode(accountConfig)] + getModuleBaseSettings(ORTB2_BLOCKING)
        def pbsServiceWithDefaultAccount = pbsServiceFactory.getService(pbsConfig)

        and: "Bid request with enabled client details"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.analytics = new PrebidAnalytics(options: new AnalyticsOptions(enableClientDetails: true))
        }

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithDefaultAccount.sendAuctionRequest(bidRequest)

        then: "Bid response should contain ext.prebid.analyticsTags with module record"
        def analyticsTagPrebid = bidResponse.ext.prebid.analytics.tags.first
        assert analyticsTagPrebid.stage == RAW_BIDDER_RESPONSE.value
        assert analyticsTagPrebid.module == ORTB2_BLOCKING.code

        and: "Analytics tag should contain results with name and success status"
        def analyticResult = analyticsTagPrebid.analyticsTags.activities.first
        assert analyticResult.status == AnalyticTagStatus.SUCCESS
        assert analyticResult.name == ModuleActivityName.ORTB2_BLOCKING

        and: "Should include appliedTo information in analytics tags results"
        verifyAll(analyticResult.results.first) {
            it.status == AnalyticTagStatus.SUCCESS_ALLOW
            it.appliedTo.bidders == [GENERIC.value]
            it.appliedTo.impIds == bidRequest.imp.id
        }

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should include analytics tag in response when request and account allow client details but default doesn't"() {
        given: "Default account with module config"
        def defaultExecutionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [RAW_BIDDER_RESPONSE])
        def defaultHooksConfiguration = new AccountHooksConfiguration(executionPlan: defaultExecutionPlan)
        def defaultAccountConfig = new AccountConfig(hooks: defaultHooksConfiguration, analytics: new AccountAnalyticsConfig(allowClientDetails: false))

        and: "Prebid server with proper default account"
        def pbsConfig = ['settings.default-account-config': encode(defaultAccountConfig)] + getModuleBaseSettings(ORTB2_BLOCKING)
        def pbsServiceWithDefaultAccount = pbsServiceFactory.getService(pbsConfig)

        and: "Bid request with enabled client details"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.analytics = new PrebidAnalytics(options: new AnalyticsOptions(enableClientDetails: true))
        }

        and: "Account in the DB"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [RAW_BIDDER_RESPONSE])
        def hooksConfiguration = new AccountHooksConfiguration(executionPlan: executionPlan)
        def accountConfig = new AccountConfig(hooks: hooksConfiguration, analytics: new AccountAnalyticsConfig(allowClientDetails: true))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithDefaultAccount.sendAuctionRequest(bidRequest)

        then: "Bid response should contain ext.prebid.analyticsTags with module record"
        def analyticsTagPrebid = bidResponse.ext.prebid.analytics.tags.first
        assert analyticsTagPrebid.stage == RAW_BIDDER_RESPONSE.value
        assert analyticsTagPrebid.module == ORTB2_BLOCKING.code

        and: "Analytics tag should contain results with name and success status"
        def analyticResult = analyticsTagPrebid.analyticsTags.activities.first
        assert analyticResult.status == AnalyticTagStatus.SUCCESS
        assert analyticResult.name == ModuleActivityName.ORTB2_BLOCKING

        and: "Should include appliedTo information in analytics tags results"
        verifyAll(analyticResult.results.first) {
            it.status == AnalyticTagStatus.SUCCESS_ALLOW
            it.appliedTo.bidders == [GENERIC.value]
            it.appliedTo.impIds == bidRequest.imp.id
        }

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)
    }

    def "PBS should not include analytics tag in response without any warnings when timeout module disabled"() {
        given: "Default account with module config"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.analytics = new PrebidAnalytics(options: new AnalyticsOptions(enableClientDetails: true))
        }

        and: "Account in the DB"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [RAW_BIDDER_RESPONSE])
        def hooksConfiguration = new AccountHooksConfiguration(executionPlan: executionPlan)
        def accountConfig = new AccountConfig(hooks: hooksConfiguration, analytics: new AccountAnalyticsConfig(allowClientDetails: true))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should not contain any analytics tag"
        assert !bidResponse?.ext?.prebid?.analytics?.tags

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext.warnings
    }

    def "PBS should not include analytics tag in response without any warnings when client details is disabled in request"() {
        given: "Default account with module config"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.analytics = new PrebidAnalytics(options: new AnalyticsOptions(enableClientDetails: false))
        }

        and: "Account in the DB"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [RAW_BIDDER_RESPONSE])
        def hooksConfiguration = new AccountHooksConfiguration(executionPlan: executionPlan)
        def accountConfig = new AccountConfig(hooks: hooksConfiguration, analytics: new AccountAnalyticsConfig(allowClientDetails: true))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "Bid response should not contain any analytics tag"
        assert !bidResponse?.ext?.prebid?.analytics?.tags

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext.warnings
    }

    def "PBS should not include analytics tag in response with warning when client details is disabled in account"() {
        given: "Default account with module config"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.analytics = new PrebidAnalytics(options: new AnalyticsOptions(enableClientDetails: true))
        }

        and: "Account in the DB"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [RAW_BIDDER_RESPONSE])
        def hooksConfiguration = new AccountHooksConfiguration(executionPlan: executionPlan)
        def accountConfig = new AccountConfig(hooks: hooksConfiguration, analytics: new AccountAnalyticsConfig(allowClientDetails: false))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "Bid response should not contain any analytics tag"
        assert !bidResponse?.ext?.prebid?.analytics?.tags

        and: "Bid response should contain warning"
        assert bidResponse.ext.warnings[PREBID]?.code == [999]
        assert bidResponse.ext.warnings[PREBID]?.message == ["analytics.options.enableclientdetails not enabled for account"]
    }

    def "PBS should not include analytics tag in response without warning when client details is disabled in account and request"() {
        given: "Default account with module config"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.analytics = new PrebidAnalytics(options: new AnalyticsOptions(enableClientDetails: false))
        }

        and: "Account in the DB"
        def executionPlan = ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, ORTB2_BLOCKING, [RAW_BIDDER_RESPONSE])
        def hooksConfiguration = new AccountHooksConfiguration(executionPlan: executionPlan)
        def accountConfig = new AccountConfig(hooks: hooksConfiguration, analytics: new AccountAnalyticsConfig(allowClientDetails: false))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        def bidResponse = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "Bid response should not contain any analytics tag"
        assert !bidResponse?.ext?.prebid?.analytics?.tags

        and: "Bid response shouldn't contain warning"
        assert !bidResponse.ext.warnings
    }
}
