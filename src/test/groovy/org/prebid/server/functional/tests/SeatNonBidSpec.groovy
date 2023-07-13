package org.prebid.server.functional.tests

import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.util.PBSUtils

import static org.mockserver.model.HttpStatusCode.NO_CONTENT_204
import static org.mockserver.model.HttpStatusCode.OK_200
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.NO_BID
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.OTHER_ERROR
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REJECTED_BY_MEDIA_TYPE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.TIMED_OUT


class SeatNonBidSpec extends BaseSpec {

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder didn't bid"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, responseStatusCode)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == NO_BID

        where:
        responseStatusCode << [OK_200, NO_CONTENT_204]
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder responded with non-SUCCESS status code"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        def successStatuses = [OK_200, NO_CONTENT_204]
        def statusCode = PBSUtils.getRandomElement(HttpStatusCode.values() - successStatuses as List)
        bidder.setResponse(bidRequest.id, bidResponse, statusCode)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == OTHER_ERROR
    }

    def "PBS shouldn't populate seatNonBid when returnAllBidStatus=true and bidder successfully bids"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid"
        assert !response.ext.seatnonbid
        assert response.seatbid
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and debug=#debug and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus and debug = #debug"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            ext.prebid.debug = debug
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid for called bidder"
        assert response.ext.seatnonbid.size() == 1

        def seatNonBid = response.ext.seatnonbid[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == NO_BID

        and: "PBS response shouldn't contain seatBid"
        assert !response.seatbid

        where:
        debug << [1, 0, null]
    }

    def "PBS shouldn't populate seatNonBid when returnAllBidStatus=false and debug=#debug and requested bidder didn't bid for any reason"() {
        given: "Default bid request with disabled returnAllBidStatus and debug = #debug"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = false
            ext.prebid.debug = debug
        }

        and: "Default bidder response without bid"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = []
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse, PBSUtils.getRandomElement(HttpStatusCode.values() as List))

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response shouldn't contain seatNonBid and seatbid for called bidder"
        assert !response.ext.seatnonbid
        assert !response.seatbid

        where:
        debug << [1, 0, null]
    }

    def "PBS should populate seatNonBid when bidder is rejected due to timeout"() {
        given: "PBS config with min and max time-out"
        def timeout = 50
        def pbsService = pbsServiceFactory.getService(["auction.biddertmax.min": timeout as String])

        and: "Default bid request with max timeout"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            tmax = timeout
        }

        and: "Default bidder response"
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest)

        and: "Set bidder response with delay"
        bidder.setResponse(bidRequest.id, bidResponse, timeout + 1)

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid and contain errors"
        def seatNonBids = response.ext.seatnonbid
        assert seatNonBids.size() == 1

        def seatNonBid = seatNonBids[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == TIMED_OUT
    }

    def "PBS should populate seatNonBid when filter-imp-media-type=true and imp doesn't contain supported media type"() {
        given: "Pbs config"
        def pbsService = pbsServiceFactory.getService(
                ["auction.filter-imp-media-type.enabled"      : "true",
                 "adapters.generic.meta-info.site-media-types": "banner"])

        and: "Default basic BidRequest with banner"
        def bidRequest = BidRequest.defaultVideoRequest.tap {
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contains seatNonBid"
        def seatNonBids = response.ext.seatnonbid
        assert seatNonBids.size() == 1

        def seatNonBid = seatNonBids[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REJECTED_BY_MEDIA_TYPE

        and: "seatbid should be empty"
        assert response.seatbid.isEmpty()
    }
}
