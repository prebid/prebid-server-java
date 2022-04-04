package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Targeting
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils

@PBSTest
class DealsSpec extends BaseSpec {

    def "PBS should choose bid with deal when preferdeals flag equal true"() {
        given: "Default basic  BidRequest with generic bidder with preferdeals = true"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.targeting = new Targeting(preferdeals: true)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())

        and: "One of the bids has dealid"
        bidResponse.seatbid.first().bid.first().dealid = PBSUtils.randomNumber
        bidResponse.seatbid.first().bid.first().price = dealBidPrice
        bidResponse.seatbid.first().bid.last().price = bidPrice
        def dealBidId = bidResponse.seatbid.first().bid.first().id

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should choose bid with deal"
        assert response.seatbid?.first()?.bid?.size() == 1
        assert response.seatbid?.first()?.bid?.first()?.id == dealBidId
        assert response.seatbid?.first()?.bid?.first()?.price == dealBidPrice

        where:
        bidPrice | dealBidPrice
        2        | bidPrice + 1
        2        | bidPrice - 1
        0        | 0
    }

    def "PBS should choose higher bid from two bids with deals"() {
        given: "Default basic  BidRequest with generic bidder with preferdeals = true"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.targeting = new Targeting(preferdeals: true)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())

        and: "Both of the bids have dealid"
        bidResponse.seatbid.first().bid.each { it.dealid = PBSUtils.randomNumber }

        and: "Set price for bids"
        def winningBidPrice = bidResponse.seatbid.first().bid.first().price + 1
        bidResponse.seatbid.first().bid.last().price = winningBidPrice

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should choose bid with higher deal price"
        assert response.seatbid?.first()?.bid?.size() == 1
        assert response.seatbid?.first()?.bid?.first()?.price == winningBidPrice
    }

    def "PBS should choose higher bid from two without deals"() {
        given: "Default basic  BidRequest with generic bidder with preferdeals = true"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.targeting = new Targeting(preferdeals: true)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())

        and: "Set price for bids"
        def winningBidPrice = bidResponse.seatbid.first().bid.first().price + 1
        bidResponse.seatbid.first().bid.last().price = winningBidPrice

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should choose bid with higher deal price"
        assert response.seatbid?.first()?.bid?.size() == 1
        assert response.seatbid?.first()?.bid?.first()?.price == winningBidPrice
    }

    def "PBS should prefer bids with dealid when multibid is enabled"() {
        given: "Default basic  BidRequest with generic bidder with preferdeals = true"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.targeting = new Targeting(preferdeals: true)

        and: "Set maxbids = 2 for default bidder"
        def maxBids = 2
        def multiBid = new MultiBid(bidder: "generic", maxBids: maxBids)
        bidRequest.ext.prebid.multibid = [multiBid]

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())

        and: "Both of the bids have dealid"
        bidResponse.seatbid.first().bid.each { it.dealid = PBSUtils.randomNumber }

        and: "Set price for bids"
        def higherBidPrice = bidResponse.seatbid.first().bid.first().price + 1
        bidResponse.seatbid.first().bid.last().price = higherBidPrice

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain maxBids bids with deal"
        def bidPrices = response.seatbid?.first()?.bid?.collect { it.price }
        assert bidPrices == bidResponse.seatbid.first().bid.collect { it.price }.sort().reverse()
    }

    def "PBS should not choose lower deal price with preferdeals equal #preferdeals flag"() {
        given: "Default basic  BidRequest with generic bidder with preferdeals"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.targeting = new Targeting(preferdeals: preferdeals)

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid << Bid.getDefaultBid(bidRequest.imp.first())

        and: "One of the bids has dealid"
        bidResponse.seatbid.first().bid.first().dealid = PBSUtils.randomNumber

        and: "Set price for bids"
        def winningBidPrice = bidResponse.seatbid.first().bid.first().price + 1
        bidResponse.seatbid.first().bid.last().price = winningBidPrice

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should choose bid with bid with higher price"
        assert response.seatbid?.first()?.bid?.size() == 1
        assert response.seatbid?.first()?.bid?.first()?.price == winningBidPrice

        where:
        preferdeals << [false, null]
    }
}
