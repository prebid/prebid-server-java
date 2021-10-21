package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@PBSTest
class StoredResponseSpec extends BaseSpec {

    @PendingFeature
    def "PBS should not fail auction with storedAuctionResponse when request bidder params doesn't satisfy json-schema"() {
        given: "BidRequest with bad bidder datatype and storedAuctionResponse"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.bidder.generic.exampleProperty = PBSUtils.randomNumber
            imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)
        }

        and: "Stored response in DB"
        def storedAuctionResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def storedResponse = new StoredResponse(resid: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Response should correspond to stored response"
        assert response.seatbid == storedAuctionResponse.seatbid
    }

    def "PBS should return info from stored auction response when it is defined in request"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)

        and: "Stored auction response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        def storedResponse = new StoredResponse(resid: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain information from stored auction response"
        assert response.id == bidRequest.id
        assert response.seatbid[0]?.seat == storedAuctionResponse.seat
        assert response.seatbid[0]?.bid?.size() == storedAuctionResponse.bid.size()
        assert response.seatbid[0]?.bid[0]?.impid == storedAuctionResponse.bid[0].impid
        assert response.seatbid[0]?.bid[0]?.price == storedAuctionResponse.bid[0].price
        assert response.seatbid[0]?.bid[0]?.id == storedAuctionResponse.bid[0].id

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should return info from stored bid response when it is defined in request"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        bidRequest.imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]

        and: "Stored bid response in DB"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest)
        def storedResponse = new StoredResponse(resid: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain information from stored bid response"
        assert response.id == bidRequest.id
        assert response.seatbid[0]?.seat == storedBidResponse.seatbid[0].seat
        assert response.seatbid[0]?.bid?.size() == storedBidResponse.seatbid[0].bid.size()
        assert response.seatbid[0]?.bid[0]?.impid == storedBidResponse.seatbid[0].bid[0].impid
        assert response.seatbid[0]?.bid[0]?.price == storedBidResponse.seatbid[0].bid[0].price
        assert response.seatbid[0]?.bid[0]?.id == storedBidResponse.seatbid[0].bid[0].id

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }
}
