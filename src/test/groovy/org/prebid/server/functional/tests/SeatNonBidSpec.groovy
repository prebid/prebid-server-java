package org.prebid.server.functional.tests

import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.pricefloors.PriceFloorData
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.ExtPrebidFloors
import org.prebid.server.functional.model.request.auction.ExtPrebidPriceFloorEnforcement
import org.prebid.server.functional.model.request.auction.Prebid
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.auction.ImpRejectionReason.NO_BID
import static org.prebid.server.functional.model.response.auction.ImpRejectionReason.OTHER_ERROR
import static org.prebid.server.functional.model.response.auction.ImpRejectionReason.REJECTED_DUE_TO_FLOOR
import static org.prebid.server.functional.model.response.auction.ImpRejectionReason.TIMEOUT

class SeatNonBidSpec extends BaseSpec {

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid = new Prebid(returnAllBidStatus: true)
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == NO_BID
    }

    def "PBS should populate seatNonBid and debug when returnAllBidStatus=true, debug=0 and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus and debug"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid = new Prebid(returnAllBidStatus: true, debug: 0)
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == NO_BID

        and: "PBS response shouldn't contain debug"
        assert !response?.ext?.debug
    }

    def "PBS shouldn't populate seatNonBid when returnAllBidStatus=false and bidder didn't bid for any reason"() {
        given: "Default bid request with disabled returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid = new Prebid(returnAllBidStatus: false)
        }

        and: "Default bidder response without bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid
        assert !response.seatbid
    }

    def "PBS shouldn't populate seatNonBid when debug=0 and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus and debug, test"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid = new Prebid(returnAllBidStatus: false, debug: 0)
        }

        and: "Default bidder response without bids"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid for called bidder"
        assert !response.ext.seatnonbid
    }

    def "PBS shouldn't populate seatNonBid with successful bids"() {
        given: "Default bid request with enabled returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Default bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid"
        assert !response.ext.seatnonbid
        assert response.seatbid
    }

    @PendingFeature
    def "PBS should populate seatNonBid when filter-imp-media-type = true, imp doesn't contain supported media type"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"      : "true",
                 "adapters.generic.meta-info.site-media-types": "video"])

        and: "Default basic BidRequest with banner"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS should remove banner imp from bidder request"
        assert response.ext?.seatnonbid
        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == OTHER_ERROR

        and: "Response should contain error"
        assert response.ext?.warnings[ErrorType.GENERIC].size() == 2
        assert response.ext?.warnings[ErrorType.GENERIC][0].code == 2
        assert response.ext?.warnings[ErrorType.GENERIC][1].code == 2
        assert response.ext?.warnings[ErrorType.GENERIC][0].message ==
                "Imp ${bidRequest.imp[0].id} does not have a supported media type and has been removed from the " +
                "request for this bidder."
        assert response.ext?.warnings[ErrorType.GENERIC][1].message ==
                "Bid request contains 0 impressions after filtering."

        and: "seatbid should be empty"
        assert response.seatbid.isEmpty()
    }

    @PendingFeature
    def "PBS should populate seatNonBid when rejected due to timeout"() {
        given: "Default bid request with max timeout"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            tmax = 1
        }

        and: "Default bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid and contain errors"
        def seatNonBid = response.ext.seatnonbid[0]
        assert response.ext.seatnonbid.size() == 1
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == TIMEOUT
    }

}
