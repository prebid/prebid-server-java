package org.prebid.server.functional

import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Unroll

import static org.prebid.server.functional.model.AccountStatus.ACTIVE
import static org.prebid.server.functional.model.AccountStatus.INACTIVE

@PBSTest
class AccountResolutionSpec extends BaseSpec {

    def "PBS should prefer account from request when account is specified in stored request"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Publisher id should correspond account from request"
        assert response.ext?.debug?.resolvedrequest?.site?.publisher?.id == ampRequest.account as String
    }

    def "PBS should reject request when account is inactive"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Save account config into DB"
        def account = new Account(uuid: bidRequest.site.publisher.id, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 401
        assert exception.responseBody.contains("Account $bidRequest.site.publisher.id is inactive")
    }

    @Unroll
    def "PBS should not reject request when account is active"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Save account config into DB"
        def account = new Account(uuid: bidRequest.site.publisher.id, status: status)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should contain seatbid"
        assert response.seatbid

        where:
        status << [null, ACTIVE]
    }

    def "PBS should not reject request when another account is inactive"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Save account config into DB"
        def account = new Account(uuid: PBSUtils.randomNumber, status: INACTIVE)
        accountDao.save(account)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should contain seatbid"
        assert response.seatbid
    }
}
