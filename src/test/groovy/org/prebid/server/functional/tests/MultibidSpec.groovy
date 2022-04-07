package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils

@PBSTest
class MultibidSpec extends BaseSpec {

    def "PBS should not return seatbid[].bid[].ext.prebid.targeting for non-winning bid in multi-bid response when includeBidderKeys = false"() {
        given: "Default basic BidRequest with generic bidder with includeBidderKeys = false"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.targeting = new Targeting(includeBidderKeys: false)

        and: "Set maxbids = 2 for default bidder"
        def maxBids = 2
        def multiBid = new MultiBid(bidder: "generic", maxBids: maxBids, targetBidderCodePrefix: PBSUtils.randomString)
        bidRequest.ext.prebid.multibid = [multiBid]

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def anotherBid = Bid.getDefaultBid(bidRequest.imp.first()).tap {
            price = bidResponse.seatbid.first().bid.first().price - 0.1
        }
        bidResponse.seatbid.first().bid << anotherBid

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should not return targeting for non-winning bid"
        assert !response.seatbid?.first()?.bid?.last()?.ext?.prebid?.targeting
    }

    def "PBS should return seatbid[].bid[].ext.prebid.targeting for non-winning bid in multi-bid response when includeBidderKeys = true"() {
        given: "Default basic BidRequest with generic bidder with includeBidderKeys = true"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.targeting = new Targeting(includeBidderKeys: true)

        and: "Set maxbids = 2 for default bidder"
        def maxBids = 2
        def multiBid = new MultiBid(bidder: "generic", maxBids: maxBids, targetBidderCodePrefix: PBSUtils.randomString)
        bidRequest.ext.prebid.multibid = [multiBid]

        and: "Default basic bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def anotherBid = Bid.getDefaultBid(bidRequest.imp.first()).tap {
            price = bidResponse.seatbid.first().bid.first().price - 0.1
        }
        bidResponse.seatbid.first().bid << anotherBid

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should return targeting for non-winning bid"
        assert response.seatbid?.first()?.bid?.last()?.ext?.prebid?.targeting
    }
}
