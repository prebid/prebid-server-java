package org.prebid.server.functional

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.util.PBSUtils

class AuctionSpec extends BaseSpec {

    def "PBS should return version in response header for auction request"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequestRawData(bidRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] ==  "pbs-java/$PBSUtils.pbsVersion"
    }
}
