package org.prebid.server.functional.tests.module.richmedia

import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountHooksConfiguration
import org.prebid.server.functional.model.config.PbsModulesConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.RichmediaFilter
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.response.auction.AnalyticResult
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.module.ModuleBaseSpec
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class RichMediaFilterSpec extends ModuleBaseSpec {

    private static final String PATTERN_NAME = PBSUtils.randomString
    private static final String PATTERN_NAME_ACCOUNT = PBSUtils.randomString
    private final PrebidServerService pbsServiceWithEnabledMediaFilter = pbsServiceFactory.getService(getRichMediaFilterSettings(PATTERN_NAME))
    private final PrebidServerService pbsServiceWithDisabledMediaFilter = pbsServiceFactory.getService(getRichMediaFilterSettings(PATTERN_NAME, false))

    def "PBS should process request without analytics when adm matches with pattern name and filter set to disabled in host config"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        def account = new Account(uuid: bidRequest.getAccountId())
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
        def account = new Account(uuid: bidRequest.getAccountId())
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsServiceWithEnabledMediaFilter.sendAuctionRequest(bidRequest)

        then: "Response header shouldn't contain any seatbid"
        assert !response.seatbid

        and: "Response should contain error of invalid creation for imp with code 350"
        def responseErrors = response.ext.errors
        assert responseErrors[ErrorType.GENERIC]*.message == ['Invalid creatives']
        assert responseErrors[ErrorType.GENERIC]*.code == [350]
        assert responseErrors[ErrorType.GENERIC].collectMany { it.impIds } == bidRequest.imp.id

        and: "Add an entry to the analytics tag for this rejected bid response"
        def analyticsTags = getAnalyticResults(response)
        assert analyticsTags.size() == 1
        def analyticResult = analyticsTags.first()
        assert analyticResult == AnalyticResult.buildFromImp(bidRequest.imp.first())

        where:
        admValue << [PATTERN_NAME, "${PBSUtils.randomString}-${PATTERN_NAME}", "${PATTERN_NAME}.${PBSUtils.randomString}"]
    }

    def "PBS should process request without analytics when adm is #admValue and filter enabled in host config"() {
        given: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
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
        def richMediaFilterConfig = new PbsModulesConfig(pbRichmediaFilter: new RichmediaFilter(filterMraid: true))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: richMediaFilterConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
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
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with enabled richMedia config in the DB"
        def richMediaFilterConfig = new PbsModulesConfig(pbRichmediaFilter: new RichmediaFilter(filterMraid: true, mraidScriptPattern: PATTERN_NAME))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: richMediaFilterConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
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
        def responseErrors = response.ext.errors
        assert responseErrors[ErrorType.GENERIC]*.message == ['Invalid creatives']
        assert responseErrors[ErrorType.GENERIC]*.code == [350]
        assert responseErrors[ErrorType.GENERIC].collectMany { it.impIds } == bidRequest.imp.id

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
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with disabled richMedia config in the DB"
        def richMediaFilterConfig = new PbsModulesConfig(pbRichmediaFilter: new RichmediaFilter(filterMraid: false, mraidScriptPattern: PATTERN_NAME))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: richMediaFilterConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
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
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with enabled richMedia config in the DB"
        def richMediaFilterConfig = new PbsModulesConfig(pbRichmediaFilter: new RichmediaFilter(filterMraid: true, mraidScriptPattern: PATTERN_NAME_ACCOUNT))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: richMediaFilterConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
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
        def responseErrors = response.ext.errors
        assert responseErrors[ErrorType.GENERIC]*.message == ['Invalid creatives']
        assert responseErrors[ErrorType.GENERIC]*.code == [350]
        assert responseErrors[ErrorType.GENERIC].collectMany { it.impIds } == bidRequest.imp.id

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
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with enabled richMedia config in the DB"
        def richMediaFilterConfig = new PbsModulesConfig(pbRichmediaFilter: new RichmediaFilter(filterMraid: true, mraidScriptPattern: PATTERN_NAME_ACCOUNT))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: richMediaFilterConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
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
        def pbsServiceWithDisabledMediaFilterHook = pbsServiceFactory.getService(getDisabledRichMediaFilterSettings(PATTERN_NAME))

        and: "BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            it.ext.prebid.trace = VERBOSE
            it.imp.first().ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Account with enabled richMedia config in the DB"
        def richMediaFilterConfig = new PbsModulesConfig(pbRichmediaFilter: new RichmediaFilter(filterMraid: true, mraidScriptPattern: PATTERN_NAME_ACCOUNT))
        def accountConfig = new AccountConfig(hooks: new AccountHooksConfiguration(modules: richMediaFilterConfig))
        def account = new Account(uuid: bidRequest.getAccountId(), config: accountConfig)
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

        where:
        admValue << [PATTERN_NAME, PATTERN_NAME_ACCOUNT]
    }

    private static List<AnalyticResult> getAnalyticResults(BidResponse response) {
        response.ext.prebid.modules?.trace?.stages?.first()
                ?.outcomes?.first()?.groups?.first()
                ?.invocationResults?.first()?.analyticStags?.activities
    }
}
