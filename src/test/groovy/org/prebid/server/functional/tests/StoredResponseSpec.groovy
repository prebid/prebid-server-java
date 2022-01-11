package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

@PBSTest
class StoredResponseSpec extends BaseSpec {

    def "PBS should fail auction with storedAuctionResponse when request bidder params doesn't satisfy json-schema"() {
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

        then: "Response should contain warnings"
        assert response.ext?.warnings[PREBID]*.code == [999, 999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["WARNING: request.imp[0].ext.prebid.bidder.generic was dropped with a reason: " +
                         "request.imp[0].ext.prebid.bidder.generic failed validation.\n" +
                         "\$.exampleProperty: integer found, string expected",
                 "WARNING: request.imp[0].ext must contain at least one valid bidder"]

        and: "Response seatbid should be empty"
        assert response.seatbid.isEmpty()
    }
}
