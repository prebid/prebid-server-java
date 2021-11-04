package org.prebid.server.functional

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.service.PrebidServerException
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

    def "PBS should return version in response header when auction request returns error"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.site = new Site(id: null, name: PBSUtils.randomString, page: null)
        bidRequest.ext.prebid.debug = 1

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.headers["x-prebid"] ==  "pbs-java/$PBSUtils.pbsVersion"
    }
}
