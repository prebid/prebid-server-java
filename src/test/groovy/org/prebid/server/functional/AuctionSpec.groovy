package org.prebid.server.functional

import org.prebid.server.functional.model.request.auction.BidRequest

class AuctionSpec extends BaseSpec {

    def "PBS should return version in response header for auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response header should contain PBS version"
        def bidderHeaders = bidder.getRecordedRequestsHeaders(bidRequest.id)[0]
        assert response.headers["x-prebid"]
        assert response.headers["x-prebid"] == bidderHeaders["x-prebid"][0]
    }
}
