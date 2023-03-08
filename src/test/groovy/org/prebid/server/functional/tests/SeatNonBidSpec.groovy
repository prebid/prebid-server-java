package org.prebid.server.functional.tests

import org.mockserver.model.HttpStatusCode
import org.prebid.server.functional.model.bidder.Rubicon
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.FAILED_TO_REQUEST_BIDS
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.NO_BID
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REJECTED_BY_MEDIA_TYPE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.TIMED_OUT
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer

class SeatNonBidSpec extends BaseSpec {

    private static final Map PBS_CONFIG = ["auction.biddertmax.max": "1",
                                           "auction.biddertmax.min": "1"]

    def "PBS should populate seatNonBid, seatBid when returnAllBidStatus=true and requested bidder didn't bid and did bid"() {
        given: "Pbs config with Rubicon"
        def prebidServerService = pbsServiceFactory.getService(
                ["adapters.${RUBICON.value}.enabled": "true",
                 "adapters.rubicon.endpoint"        : "$networkServiceContainer.rootUri/auction".toString()])

        and: "Default bid request to Rubicon"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            imp.first().ext.prebid.bidder.generic = null
            imp.first().ext.prebid.bidder.rubicon = new Rubicon(accountId: PBSUtils.randomNumber,
                    siteId: PBSUtils.randomNumber, zoneId: PBSUtils.randomNumber)
        }

        and: "Second imp"
        bidRequest.imp[1] = Imp.getDefaultImpression()
        bidRequest.imp[1].ext.prebid.bidder.generic = null
        bidRequest.imp[1].ext.prebid.bidder.rubicon = new Rubicon(accountId: PBSUtils.randomNumber,
                siteId: PBSUtils.randomNumber, zoneId: PBSUtils.randomNumber)

        and: "Set up bid just for the first imp"
        def bids = [Bid.getDefaultBid(bidRequest.imp[0])]
        def seatBidResponse = new SeatBid(bid: bids, seat: RUBICON)
        def bidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid = [seatBidResponse]
        }

        and: "Set bidder response"
        bidder.setResponse(bidRequest.id, bidResponse)

        when: "PBS processes auction request"
        def response = prebidServerService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid"
        def seatNonBids = response.ext.seatnonbid
        assert seatNonBids.size() == 1

        def seatNonBid = seatNonBids[0]
        assert seatNonBid.seat == RUBICON.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[1].id
        assert seatNonBid.nonBid[0].statusCode == NO_BID

        then: "PBS response should contain seatBid"
        def seatBids = response.seatbid
        assert seatBids.size() == 1

        def seatBid = response.seatbid[0]
        assert seatBid.seat == RUBICON
        assert seatBid.bid.first().id == bids.first().id
    }

    def "PBS should populate seatNonBid when returnAllBidStatus=true and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
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
        assert seatNonBid.nonBid[0].statusCode == FAILED_TO_REQUEST_BIDS
    }

    def "PBS should populate seatNonBid and debug when returnAllBidStatus=true, debug=0 and requested bidder didn't bid for any reason"() {
        given: "Default bid request with returnAllBidStatus and debug"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = true
            ext.prebid.debug = 0
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
        assert seatNonBid.nonBid[0].statusCode == FAILED_TO_REQUEST_BIDS

        and: "PBS response shouldn't contain debug"
        assert !response?.ext?.debug
    }

    def "PBS shouldn't populate seatNonBid when returnAllBidStatus=false and bidder didn't bid for any reason"() {
        given: "Default bid request with disabled returnAllBidStatus"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            ext.prebid.returnAllBidStatus = false
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
            ext.prebid.returnAllBidStatus = false
            ext.prebid.debug = 0
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

        then: "PBS response shouldn't contain seatNonBid and should contain seatbid"
        assert response.ext.seatnonbid.nonBid.size() == 0
        assert response.seatbid
    }

    def "PBS should populate seatNonBid when rejected due to timeout"() {
        def pbsService = pbsServiceFactory.getService(PBS_CONFIG)
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
        def response = pbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contain seatNonBid and contain errors"
        def seatNonBids = response.ext.seatnonbid
        assert seatNonBids.size() == 1

        def seatNonBid = seatNonBids[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == TIMED_OUT
    }

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
