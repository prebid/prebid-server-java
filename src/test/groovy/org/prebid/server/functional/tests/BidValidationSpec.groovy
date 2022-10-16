package org.prebid.server.functional.tests

import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.MultiBid
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

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

    def "PBS should reject request for bidder and emit warning when both site and app present"() {
        given: "Default basic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.site = new Site(id: null, name: PBSUtils.randomString, page: null)

        and: "Set app"
        bidRequest.app = App.defaultApp

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.message ==
                ["BidRequest contains App and Site for bidder generic. Request rejected."]
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

    def "PBS should only drop invalid bid without discarding whole seat"() {
        given: "Default basic  BidRequest with generic bidder"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.debug = 1
        bidRequest.ext.prebid.multibid = [new MultiBid(bidder: GENERIC, maxBids: 2)]

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

    def "PBS should update 'adapter.generic.requests.bid_validation' metric when bid validation error appears"() {
        given: "Initial 'adapter.generic.requests.bid_validation' metric value"
        def initialMetricValue = getCurrentMetricValue("adapter.generic.requests.bid_validation")

        and: "Bid request"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Set invalid bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].impid = PBSUtils.randomNumber as String
        }
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bid validation metric value is incremented"
        def metrics = defaultPbsService.sendCollectedMetricsRequest()
        assert metrics["adapter.generic.requests.bid_validation"] == initialMetricValue + 1
    }

    def "PBS should return an error when GVL Id alias refers to unknown bidder alias"() {
        given: "Default basic BidRequest with aliasgvlids and aliases"
        def bidderName = PBSUtils.randomString
        def validId = 1
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.aliasgvlids = [(bidderName): validId]
        bidRequest.ext.prebid.aliases = [(PBSUtils.randomString): GENERIC]

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.aliasgvlids. " +
                "vendorId ${validId} refers to unknown bidder alias: ${bidderName}")
    }

    def "PBS should return an error when GVL ID alias value is lower that one"() {
        given: "Default basic BidRequest with aliasgvlids and aliases"
        def bidderName = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.ext.prebid.aliasgvlids = [(bidderName): invalidId]
        bidRequest.ext.prebid.aliases = [(bidderName): GENERIC]

        when: "Sending auction request to PBS"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody.contains("Invalid request format: request.ext.prebid.aliasgvlids. " +
                "Invalid vendorId ${invalidId} for alias: ${bidderName}. Choose a different vendorId, or remove this entry.")

        where:
        invalidId << [PBSUtils.randomNegativeNumber, 0]
    }
}
