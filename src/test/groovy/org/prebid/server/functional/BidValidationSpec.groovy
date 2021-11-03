package org.prebid.server.functional

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature
import spock.lang.Unroll

@PBSTest
class BidValidationSpec extends BaseSpec {

    @PendingFeature
    def "PBS should return error type invalid bid when bid does not pass validation with error"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Default basic bid with seatbid[].bid[].price = null"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid.first().price = null

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain basic fields"
        assert response.ext?.errors[ErrorType.GENERIC]*.code == [5]
        assert response.ext?.errors[ErrorType.GENERIC]*.message ==
                ["Bid \"${bidResponse.seatbid.first().bid.first().id}\" does not contain a 'price'" as String]
    }

    def "PBS should remove site object and emit warning when both site and app present, debug mode is enabled"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.site = new Site(id: null, name: PBSUtils.randomString, page: null)
        bidRequest.ext.prebid.debug = 1

        and: "Set app"
        bidRequest.app = App.defaultApp

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should not contain site"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.site

        and: "Response should contain debug warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.message ==
                ["BidRequest contains app and site. Removed site object"]
    }

    def "PBS should remove site object and emit warning when both site and app present, debug mode is disabled"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.site = new Site(id: null, name: PBSUtils.randomString, page: null)
        bidRequest.ext.prebid.debug = 0

        and: "Set app"
        bidRequest.app = App.defaultApp

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should not contain site"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.site

        and: "Response should contain debug warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.message ==
                ["BidRequest contains app and site. Removed site object"]
    }

    def "PBS should validate site when it is present"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.site = new Site(id: null, name: PBSUtils.randomString, page: null)
        bidRequest.ext.prebid.debug = 1

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("request.site should include at least one of request.site.id or request.site.page")
    }

    def "PBS should treat bids with 0 price as valid when deal id is present"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Bid response with 0 price bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid.first().bid.first().dealid = PBSUtils.randomNumber
        bidResponse.seatbid.first().bid.first().price = 0

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Zero price bid should be present in the PBS response"
        assert response.seatbid?.first()?.bid*.id == [bidResponse.seatbid.first().bid.first().id]

        and: "No errors should be emitted in the debug"
        assert !response.ext?.errors
        assert !response.ext?.warnings
    }

    @Unroll
    def "PBS should drop invalid bid and emit debug error when bid price is #bidPrice and deal id is #dealId"() {
        given: "Default basic BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1

        and: "Bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def bid = bidResponse.seatbid.first().bid.first()
        bid.dealid = dealId
        bid.price = bidPrice
        def bidId = bid.id

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Invalid bid should be deleted"
        assert response.seatbid.size() == 0

        and: "PBS should emit an error"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message ==
                ["Dropped bid '$bidId'. Does not contain a positive (or zero if there is a deal) 'price'" as String]

        where:
        bidPrice                      | dealId
        PBSUtils.randomNegativeNumber | null
        PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        0                             | null
        null                          | PBSUtils.randomNumber
        null                          | null
    }

    @Unroll
    def "PBS should only drop invalid bid without discarding whole seat"() {
        given: "Default basic  BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1
        bidRequest.ext.prebid.multibid = [new MultiBid(bidder: BidderName.GENERIC.value, maxBids: 2)]

        and: "Bid response with 2 bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        bidResponse.seatbid[0].bid << Bid.getDefaultBid(bidRequest.imp.first())

        and: "One of the bids is invalid"
        def invalidBid = bidResponse.seatbid.first().bid.first()
        invalidBid.dealid = dealId
        invalidBid.price = bidPrice
        def validBidId = bidResponse.seatbid.first().bid.last().id

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Invalid bids should be deleted"
        assert response.seatbid?.first()?.bid*.id == [validBidId]

        and: "PBS should emit an error"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message ==
                ["Dropped bid '$invalidBid.id'. Does not contain a positive (or zero if there is a deal) 'price'" as String]

        where:
        bidPrice                      | dealId
        PBSUtils.randomNegativeNumber | null
        PBSUtils.randomNegativeNumber | PBSUtils.randomNumber
        0                             | null
        null                          | PBSUtils.randomNumber
        null                          | null
    }
}
