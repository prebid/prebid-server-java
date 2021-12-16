package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

@PBSTest
class StoredResponseSpec extends BaseSpec {

    @PendingFeature
    def "PBS should not fail auction with storedAuctionResponse when request bidder params doesn't satisfy json-schema"() {
        given: "BidRequest with bad bidder datatype and storedAuctionResponse"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generic.exampleProperty = PBSUtils.randomNumber
            imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)
        }

        and: "Stored response in DB"
        def responseData = BidResponse.getDefaultBidResponse(bidRequest)
        def storedResponse = new StoredResponse(resid: storedResponseId, responseData: responseData)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should correspond to stored response"
        assert response.seatbid == responseData.seatbid
    }
}
