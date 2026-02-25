package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerService

import static org.apache.http.HttpStatus.SC_UNAUTHORIZED
import static org.prebid.server.functional.model.AccountStatus.INACTIVE
import static org.prebid.server.functional.model.response.BidderErrorCode.BAD_INPUT
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.model.response.auction.NoBidResponse.UNKNOWN_ERROR

class OrtbErrorResponseSpec extends BaseSpec {

    private static final Closure<String> ACCOUNT_INVALID_MESSAGE = { accountId -> "Account ${accountId} is inactive" }
    private static final PrebidServerService disabledOrtbErrorResponseConfigPbsService = pbsServiceFactory.getService(["auction.ortb-error-response": "false"])

    @Override
    def cleanupSpec() {
        pbsServiceFactory.removeContainer(["auction.ortb-error-response": "false"])
    }

    def "PBS auction should provide proper ORTB error response when ortb-error-response host config is enabled"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Invalid account in DB"
        def account = new Account(uuid: bidRequest.accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = disabledOrtbErrorResponseConfigPbsService.sendAuctionRequest(bidRequest, SC_UNAUTHORIZED)

        then: "Response should be with error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMessage == [ACCOUNT_INVALID_MESSAGE(bidRequest.accountId)]
        }
    }

    def "PBS auction should provide raw error response when ortb-error-response host config is disabled"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Invalid account in DB"
        def account = new Account(uuid: bidRequest.accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = disabledOrtbErrorResponseConfigPbsService.sendAuctionRequestRaw(bidRequest)

        then: "Response should be with error"
        assert response.statusCode == SC_UNAUTHORIZED
        assert response.responseBody == ACCOUNT_INVALID_MESSAGE(bidRequest.accountId)
    }

    def "PBS auction should provide raw error response and ignore host config when ortb-errors host config is disabled"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.ortbErrors = false
        }

        and: "Invalid account in DB"
        def account = new Account(uuid: bidRequest.accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequestRaw(bidRequest)

        then: "Response should be with error"
        assert response.statusCode == SC_UNAUTHORIZED
        assert response.responseBody == ACCOUNT_INVALID_MESSAGE(bidRequest.accountId)

        where:
        pbsService << [disabledOrtbErrorResponseConfigPbsService, defaultPbsService]
    }

    def "PBS auction should provide proper ORTB error response and ignore host config when ortb-errors host config is enabled"() {
        given: "Default bid request"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.ortbErrors = true
        }

        and: "Invalid account in DB"
        def account = new Account(uuid: bidRequest.accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest, SC_UNAUTHORIZED)

        then: "Response should be with error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMessage == [ACCOUNT_INVALID_MESSAGE(bidRequest.accountId)]
        }

        where:
        pbsService << [disabledOrtbErrorResponseConfigPbsService, defaultPbsService]
    }

    def "PBS amp should provide proper ORTB error response when ortb-error-response host config is enabled"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        
        and: "Default basic BidRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Invalid account in DB"
        def account = new Account(uuid: ampRequest.account, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes amp request"
        def response = disabledOrtbErrorResponseConfigPbsService.sendAmpRequest(ampRequest, SC_UNAUTHORIZED)

        then: "Response should be with error"
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMessage == [ACCOUNT_INVALID_MESSAGE(ampRequest.account)]
        }
    }

    def "PBS amp should provide raw error response when ortb-error-response host config is disabled"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default basic BidRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Invalid account in DB"
        def account = new Account(uuid: ampRequest.account, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes amp request"
        def response = disabledOrtbErrorResponseConfigPbsService.sendAmpRequestRaw(ampRequest)

        then: "Response should be with error"
        assert response.statusCode == SC_UNAUTHORIZED
        assert response.responseBody == ACCOUNT_INVALID_MESSAGE(ampRequest.account)
    }

    def "PBS amp should provide raw error response and ignore host config when ortb-errors host config is disabled"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default basic BidRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.ortbErrors = false
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Invalid account in DB"
        def account = new Account(uuid: ampRequest.account, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes amp request"
        def response = pbsService.sendAmpRequestRaw(ampRequest)

        then: "Response should be with error"
        assert response.statusCode == SC_UNAUTHORIZED
        assert response.responseBody == ACCOUNT_INVALID_MESSAGE(ampRequest.account)

        where:
        pbsService << [disabledOrtbErrorResponseConfigPbsService, defaultPbsService]
    }

    def "PBS amp should provide proper ORTB error response and ignore host config when ortb-errors host config is enabled"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default basic BidRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.ortbErrors = true
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Invalid account in DB"
        def account = new Account(uuid: ampRequest.account, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes amp request"
        def response = pbsService.sendAmpRequest(ampRequest, SC_UNAUTHORIZED)

        then: "Response should be with error"
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMessage == [ACCOUNT_INVALID_MESSAGE(ampRequest.account)]
        }

        where:
        pbsService << [disabledOrtbErrorResponseConfigPbsService, defaultPbsService]
    }
}
