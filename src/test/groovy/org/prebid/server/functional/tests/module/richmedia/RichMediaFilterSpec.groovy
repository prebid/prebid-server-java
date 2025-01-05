package org.prebid.server.functional.tests.module.richmedia

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.ExecutionPlan
import org.prebid.server.functional.model.config.HookId
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.RichmediaFilter
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.response.auction.AnalyticResult
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.config.ModuleHookImplementation.PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE
import static org.prebid.server.functional.model.config.ModuleName.PB_RICHMEDIA_FILTER
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.Endpoint.OPENRTB2_AUCTION
import static org.prebid.server.functional.model.config.Stage.ALL_PROCESSED_BID_RESPONSES
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class RichMediaFilterSpec extends ModuleBaseSpec {

    private static final String PATTERN_NAME = PBSUtils.randomString
    private static final String PATTERN_NAME_ACCOUNT = PBSUtils.randomString
    private static final Map<String, String> DISABLED_FILTER_SPECIFIC_PATTERN_NAME_CONFIG = getRichMediaFilterSettings(PATTERN_NAME, false)
    private static final Map<String, String> SPECIFIC_PATTERN_NAME_CONFIG = getRichMediaFilterSettings(PATTERN_NAME)
    private static final Map<String, String> SNAKE_SPECIFIC_PATTERN_NAME_CONFIG =  (getRichMediaFilterSettings(PATTERN_NAME) +
            ["hooks.host-execution-plan": encode(ExecutionPlan.getSingleEndpointExecutionPlan(OPENRTB2_AUCTION, PB_RICHMEDIA_FILTER, [ALL_PROCESSED_BID_RESPONSES]).tap {
                endpoints.values().first().stages.values().first().groups.first.hookSequenceSnakeCase = [new HookId(moduleCodeSnakeCase: PB_RICHMEDIA_FILTER.code, hookImplCodeSnakeCase: "${PB_RICHMEDIA_FILTER.code}-${ALL_PROCESSED_BID_RESPONSES.value}-hook")]
            })]).collectEntries { key, value -> [(key.toString()): value.toString()] }

    private static PrebidServerService pbsServiceWithDisabledMediaFilter
    private static PrebidServerService pbsServiceWithEnabledMediaFilter
    private static PrebidServerService pbsServiceWithEnabledMediaFilterAndDifferentCaseStrategy

    def setupSpec() {
        pbsServiceWithDisabledMediaFilter = pbsServiceFactory.getService(DISABLED_FILTER_SPECIFIC_PATTERN_NAME_CONFIG)
        pbsServiceWithEnabledMediaFilter = pbsServiceFactory.getService(SPECIFIC_PATTERN_NAME_CONFIG)
        pbsServiceWithEnabledMediaFilterAndDifferentCaseStrategy = pbsServiceFactory.getService(SNAKE_SPECIFIC_PATTERN_NAME_CONFIG)
    }

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(DISABLED_FILTER_SPECIFIC_PATTERN_NAME_CONFIG)
        pbsServiceFactory.removeContainer(SPECIFIC_PATTERN_NAME_CONFIG)
        pbsServiceFactory.removeContainer(SNAKE_SPECIFIC_PATTERN_NAME_CONFIG)
    }

    def "PBS should process request without rich media module when host config have empty settings"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = PBSUtils.randomString
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account with enabled richMedia config in the DB"
        def account = getAccountWithRichmediaFilter(bidRequest.accountId, filterMraid, mraidScriptPattern)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithMultipleModules.sendAuctionRequest(bidRequest)

        then: "Response header should contain seatbid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors of invalid creation"
        assert !response.ext.errors

        and: "Response shouldn't contain analytics"
        assert !getAnalyticResults(response)

        where:
        filterMraid      | mraidScriptPattern
        true             | PBSUtils.randomString
        true             | null
        false            | null
        null             | null
    }

    def "PBS should process request without analytics when adm matches with pattern name and filter set to disabled in host config"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = admValue as String
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithDisabledMediaFilter.sendAuctionRequest(bidRequest)

        then: "Response header should contain seatbid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors of invalid creation"
        assert !response.ext.errors

        and: "Response shouldn't contain analytics"
        assert !getAnalyticResults(response)

        where:
        admValue << [PATTERN_NAME, "${PBSUtils.randomString}-${PATTERN_NAME}", "${PATTERN_NAME}-${PBSUtils.randomString}"]
    }

    def "PBS should reject request with error and provide analytic when adm matches with pattern name and filter set to enabled in host config"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = admValue as String
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithEnabledMediaFilter.sendAuctionRequest(bidRequest)

        then: "Response header shouldn't contain any seatbid"
        assert !response.seatbid

        and: "Response should contain error of invalid creation for imp with code 350"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_INVALID_CREATIVE

        and: "Add an entry to the analytics tag for this rejected bid response"
        def analyticsTags = getAnalyticResults(response)
        assert analyticsTags.size() == 1
        def analyticResult = analyticsTags.first()
        assert analyticResult == AnalyticResult.buildFromImp(bidRequest.imp.first())

        where:
        admValue << [PATTERN_NAME, "${PBSUtils.randomString}-${PATTERN_NAME}", "${PATTERN_NAME}.${PBSUtils.randomString}"]
    }

    def "PBS should process request without analytics when adm is #admValue and filter enabled in config"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = admValue as String
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account with enabled richMedia config in the DB"
        def account = getAccountWithRichmediaFilter(bidRequest.accountId, true, null)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithEnabledMediaFilter.sendAuctionRequest(bidRequest)

        then: "Response header should contain seatbid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors of invalid creation"
        assert !response.ext.errors

        and: "Response shouldn't contain analytics"
        assert !getAnalyticResults(response)

        where:
        admValue << [null, '', PBSUtils.randomString]
    }

    def "PBS should prioritize account config and reject request with error and provide analytic when adm matches with pattern name and filter disabled in host config but enabled in account config"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with enabled richMedia config in the DB"
        def account = getAccountWithRichmediaFilter(bidRequest.accountId, true, PATTERN_NAME)
        accountDao.save(account)

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = admValue as String
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithDisabledMediaFilter.sendAuctionRequest(bidRequest)

        then: "Response header shouldn't contain any seatbid"
        assert !response.seatbid

        and: "Response should contain error of invalid creation for imp with code 350"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_INVALID_CREATIVE

        and: "Add an entry to the analytics tag for this rejected bid response"
        def analyticsTags = getAnalyticResults(response)
        assert analyticsTags.size() == 1
        def analyticResult = analyticsTags.first()
        assert analyticResult == AnalyticResult.buildFromImp(bidRequest.imp.first())

        where:
        admValue << [PATTERN_NAME, "${PBSUtils.randomString}-${PATTERN_NAME}", "${PATTERN_NAME}-${PBSUtils.randomString}"]
    }

    def "PBS should prioritize account config and process request without analytics when adm matches with pattern name and filter enabled in host config but disabled in account config"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with disabled richMedia config in the DB"
        def account = getAccountWithRichmediaFilter(bidRequest.accountId, false, PATTERN_NAME)
        accountDao.save(account)

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = admValue as String
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithEnabledMediaFilter.sendAuctionRequest(bidRequest)

        then: "Response header should contain seatbid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors of invalid creation"
        assert !response.ext.errors

        and: "Response shouldn't contain analytics"
        assert !getAnalyticResults(response)

        where:
        admValue << [PATTERN_NAME, "${PBSUtils.randomString}-${PATTERN_NAME}", "${PATTERN_NAME}-${PBSUtils.randomString}"]
    }

    def "PBS should prioritize account config and reject request with error and provide analytic when adm matches with account pattern and both host and account configs are enabled"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with enabled richMedia config in the DB"
        def account = getAccountWithRichmediaFilter(bidRequest.accountId, true, PATTERN_NAME_ACCOUNT)
        accountDao.save(account)

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = PATTERN_NAME_ACCOUNT
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithEnabledMediaFilter.sendAuctionRequest(bidRequest)

        then: "Response header shouldn't contain any seatbid"
        assert !response.seatbid

        and: "Response should contain error of invalid creation for imp with code 350"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_INVALID_CREATIVE

        and: "Add an entry to the analytics tag for this rejected bid response"
        def analyticsTags = getAnalyticResults(response)
        assert analyticsTags.size() == 1
        def analyticResult = analyticsTags.first()
        assert analyticResult == AnalyticResult.buildFromImp(bidRequest.imp.first())
    }

    def "PBS should prioritize account config and process request without analytics when adm matches with host pattern and both host and account configs are enabled"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with enabled richMedia config in the DB"
        def account = getAccountWithRichmediaFilter(bidRequest.accountId, true, PATTERN_NAME_ACCOUNT)
        accountDao.save(account)

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = PATTERN_NAME
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithEnabledMediaFilter.sendAuctionRequest(bidRequest)

        then: "Response header should contain seatbid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors of invalid creation"
        assert !response.ext.errors

        and: "Response shouldn't contain analytics"
        assert !getAnalyticResults(response)
    }

    def "PBS should process any request without analytics and errors when pb-richmedia-filter hook is disabled"() {
        given: "PBS with disabled pb-richmedia-filter hook"
        def pbsConfig = getRichMediaFilterSettings(PATTERN_NAME) +
                getModuleBaseSettings(PB_RICHMEDIA_FILTER, false) +
                ["hooks.host-execution-plan": null]
        def pbsServiceWithDisabledMediaFilterHook = pbsServiceFactory.getService(pbsConfig)

        and: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with enabled richMedia config in the DB"
        def richMediaFilterConfig = new PbsModulesConfig(pbRichmediaFilter: new RichmediaFilter(filterMraid: true, mraidScriptPattern: PATTERN_NAME_ACCOUNT))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: richMediaFilterConfig))
        def account = new Account(uuid: bidRequest.accountId, config: accountConfig)
        accountDao.save(account)

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = admValue
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = pbsServiceWithDisabledMediaFilterHook.sendAuctionRequest(bidRequest)

        then: "Response header should contain seatbid"
        assert response.seatbid.size() == 1

        and: "Response shouldn't contain errors of invalid creation"
        assert !response.ext.errors

        and: "Response shouldn't contain analytics"
        assert !getAnalyticResults(response)

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(pbsConfig)

        where:
        admValue << [PATTERN_NAME, PATTERN_NAME_ACCOUNT]
    }

    def "PBS should reject request with error and provide analytic when adm matches with pattern name and filter set to enabled in host config with different name case"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            it.seatbid[0].bid[0].adm = admValue as String
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithEnabledMediaFilterAndDifferentCaseStrategy.sendAuctionRequest(bidRequest)

        then: "Response header shouldn't contain any seatbid"
        assert !response.seatbid

        and: "Response should contain error of invalid creation for imp with code 350"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == RESPONSE_REJECTED_INVALID_CREATIVE

        and: "Add an entry to the analytics tag for this rejected bid response"
        def analyticsTags = getAnalyticResults(response)
        assert analyticsTags.size() == 1
        def analyticResult = analyticsTags.first()
        assert analyticResult == AnalyticResult.buildFromImp(bidRequest.imp.first())

        where:
        admValue << [PATTERN_NAME, "${PBSUtils.randomString}-${PATTERN_NAME}", "${PATTERN_NAME}.${PBSUtils.randomString}"]
    }

    private static List<AnalyticResult> getAnalyticResults(BidResponse response) {
        response.ext.prebid.modules?.trace?.stages?.first()
                ?.outcomes?.first()?.groups?.first()
                ?.invocationResults?.first()?.analyticsTags?.activities
    }

    private static Account getAccountWithRichmediaFilter(String accountId, Boolean filterMraid, String mraidScriptPattern) {
        getAccountWithModuleConfig(accountId, [PB_RICHMEDIA_FILTER_ALL_PROCESSED_RESPONSES]).tap {
            it.config.hooks.modules.pbRichmediaFilter = new RichmediaFilter(filterMraid: filterMraid, mraidScriptPattern: mraidScriptPattern)
        }
    }
}
