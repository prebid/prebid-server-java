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

class OrtbValidationSpec extends BaseSpec {

    private static final PrebidServerService baseValidationPbsService = pbsServiceFactory.getService(["auction.ortb-error-response": "false"])
    private static final Closure<String> ACCOUNT_INVALID_MESSAGE = { accountId -> "Account ${accountId} is inactive" }

    def "PBS auction should provide proper ORTB error response when ortb-error-response host config is enabled"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = baseValidationPbsService.sendAuctionRequest(bidRequest, SC_UNAUTHORIZED)

        then: "Request should fail with error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMassage == [ACCOUNT_INVALID_MESSAGE(bidRequest.accountId)]
        }
    }

    def "PBS auction should provide raw error response when ortb-error-response host config is disabled"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = baseValidationPbsService.sendAuctionRequestRaw(bidRequest)

        then: "Request should fail with error"
        assert response.statusCode == SC_UNAUTHORIZED
        assert response.responseBody == ACCOUNT_INVALID_MESSAGE(bidRequest.accountId)
    }

    def "PBS auction should provide raw error response and ignore host config when ortb-errors host config is disabled"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.ortbErrors = false
        }

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequestRaw(bidRequest)

        then: "Request should fail with error"
        assert response.statusCode == SC_UNAUTHORIZED
        assert response.responseBody == ACCOUNT_INVALID_MESSAGE(bidRequest.accountId)

        where:
        pbsService << [baseValidationPbsService, defaultPbsService]
    }

    def "PBS auction should provide proper ORTB error response and ignore host config when ortb-errors host config is enabled"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.ortbErrors = true
        }

        and: "Account in the DB"
        def account = new Account(uuid: bidRequest.accountId, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest, SC_UNAUTHORIZED)

        then: "Request should fail with error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMassage == [ACCOUNT_INVALID_MESSAGE(bidRequest.accountId)]
        }

        where:
        pbsService << [baseValidationPbsService, defaultPbsService]
    }

    def "PBS amp should provide proper ORTB error response when ortb-error-response host config is enabled"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        
        and: "Default basic BidRequest with generic bidder"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def account = new Account(uuid: ampRequest.account, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = baseValidationPbsService.sendAmpRequest(ampRequest, SC_UNAUTHORIZED)

        then: "Request should fail with error"
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]
            it.errorMassage == [ACCOUNT_INVALID_MESSAGE(ampRequest.account)]
        }
    }

    def "PBS amp should provide raw error response when ortb-error-response host config is disabled"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default basic BidRequest with generic bidder"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def account = new Account(uuid: ampRequest.account, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = baseValidationPbsService.sendAmpRequestRaw(ampRequest)

        then: "Request should fail with error"
        assert response.responseCode == SC_UNAUTHORIZED
        assert response.responseBody == ACCOUNT_INVALID_MESSAGE(ampRequest.account)
    }

    def "PBS amp should provide raw error response and ignore host config when ortb-errors host config is disabled"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default basic BidRequest with generic bidder"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.ortbErrors = false
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def account = new Account(uuid: ampRequest.account, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAmpRequestRaw(ampRequest)

        then: "Request should fail with error"
        assert response.statusCode == SC_UNAUTHORIZED
        assert response.responseBody == ACCOUNT_INVALID_MESSAGE(ampRequest.account)

        where:
        pbsService << [baseValidationPbsService, defaultPbsService]
    }

    def "PBS amp should provide proper ORTB error response and ignore host config when ortb-errors host config is enabled"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default basic BidRequest with generic bidder"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.ortbErrors = true
            setAccountId(ampRequest.account)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Account in the DB"
        def account = new Account(uuid: ampRequest.account, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest, SC_UNAUTHORIZED)

        then: "Request should fail with error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [BAD_INPUT]

            it.errorMassage == [ACCOUNT_INVALID_MESSAGE(ampRequest.account)]
        }

        where:
        pbsService << [baseValidationPbsService, defaultPbsService]
    }
}
