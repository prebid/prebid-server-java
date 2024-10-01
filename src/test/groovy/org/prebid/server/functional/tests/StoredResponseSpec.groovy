package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.request.auction.StoredBidResponse
import org.prebid.server.functional.model.response.auction.Bid
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

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
        def storedResponse = new StoredResponse(responseId: storedResponseId,
                storedAuctionResponse: storedAuctionResponse)
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
        def storedResponse = new StoredResponse(responseId: storedResponseId,
                storedAuctionResponse: storedAuctionResponse)
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
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
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

    def "PBS should replace bid impId on imp id when bid impId has macro ##PBSIMPID##"() {
        given: "Default basic BidRequest with stored response"
        def storedResponseId = PBSUtils.randomNumber
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].ext.prebid.storedBidResponse = [new StoredBidResponse(id: storedResponseId, bidder: GENERIC)]
        }

        and: "Stored bid response in DB with marco id"
        def storedBidResponse = BidResponse.getDefaultBidResponse(bidRequest).tap {
            seatbid[0].bid[0].impid = "##PBSIMPID##"
        }
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedBidResponse: storedBidResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain information from stored bid response and change bid.impId on imp.id"
        assert response.id == bidRequest.id
        assert response.seatbid[0]?.seat == storedBidResponse.seatbid[0].seat
        assert response.seatbid[0]?.bid?.size() == storedBidResponse.seatbid[0].bid.size()
        assert response.seatbid[0]?.bid[0]?.impid == bidRequest.imp[0].id
        assert response.seatbid[0]?.bid[0]?.price == storedBidResponse.seatbid[0].bid[0].price
        assert response.seatbid[0]?.bid[0]?.id == storedBidResponse.seatbid[0].bid[0].id

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should return warning when imp[0].ext.prebid.storedAuctionResponse contain seatBid"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse().tap {
            id = storedResponseId
            seatBids = [storedAuctionResponse]
        }

        and: "Stored auction response in DB"
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain warning information"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message ==
                ['WARNING: request.imp[0].ext.prebid.storedauctionresponse.seatbidarr is not supported at the imp level']

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should set seatBid from request storedAuctionResponse.seatBid when ext.prebid.storedAuctionResponse.seatBid present and id is null"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        bidRequest.ext.prebid.storedAuctionResponse = new StoredAuctionResponse().tap {
            id = null
            seatBids = [storedAuctionResponse]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain same stored auction response as requested"
        assert response.seatbid == [storedAuctionResponse]

        and: "PBs should emit warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message ==
                ["no auction. response defined by storedauctionresponse" as String]

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should set seatBid in response from db when ext.prebid.storedAuctionResponse.seatBid not defined and id is defined"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        bidRequest.ext.prebid.storedAuctionResponse = new StoredAuctionResponse().tap {
            id = storedResponseId
            seatBids = null
        }

        and: "Stored auction response in DB"
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain same stored auction response as requested"
        assert response.seatbid == [storedAuctionResponse]

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should perform usually auction call when storedActionResponse when id and seatbid are null"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        bidRequest.ext.prebid.storedAuctionResponse = new StoredAuctionResponse().tap {
            it.id = null
            it.seatBids = null
        }

        and: "Stored auction response in DB"
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain same stored auction response as requested"
        assert response.seatbid

        and: "PBs shouldn't emit warnings"
        assert !response.ext?.warnings

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 1

        where:
        seatbid << [null, [null]]
    }

    def "PBS return warning when id is null and seatbid with null"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        bidRequest.ext.prebid.storedAuctionResponse = new StoredAuctionResponse().tap {
            it.id = null
            it.seatBids = [null]
        }

        and: "Stored auction response in DB"
        def storedResponse = new StoredResponse(responseId: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain warning information"
        assert response.ext?.warnings[ErrorType.PREBID]*.message.contains('SeatBid can\'t be null in stored response')

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should set seatBid in response from single imp.ext.prebid.storedBidResponse.seatbidobj when it is defined"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(seatBidObject:  storedAuctionResponse)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain same stored auction response as requested"
        assert convertToComparableSeatBid(response.seatbid) == [storedAuctionResponse]

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should throw error when imp.ext.prebid.storedBidResponse.seatbidobj is with empty seatbid"() {
        given: "Default basic BidRequest with empty stored response"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(seatBidObject:  new SeatBid())

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS throws an exception"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == 'Invalid request format: Seat can\'t be empty in stored response seatBid'

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should throw error when imp.ext.prebid.storedBidResponse.seatbidobj is with empty bids"() {
        given: "Default basic BidRequest with empty bids for stored response"
        def bidRequest = BidRequest.defaultBidRequest
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(seatBidObject:  new SeatBid(bid: [], seat: GENERIC))

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "PBS throws an exception"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == 'Invalid request format: There must be at least one bid in stored response seatBid'

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should prefer seatbidobj over storedAuctionResponse.id from imp when both are present"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse().tap {
            id = PBSUtils.randomString
            seatBidObject = storedAuctionResponse
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain same stored auction response as requested"
        assert convertToComparableSeatBid(response.seatbid) == [storedAuctionResponse]

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should set seatBids in response from multiple imp.ext.prebid.storedBidResponse.seatbidobj when it is defined"() {
        given: "BidRequest with multiple imps"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp = [impWithSeatBidObject, impWithSeatBidObject]
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain same stored auction response bids as requested"
        assert convertToComparableSeatBid(response.seatbid).bid.flatten().sort() ==
                bidRequest.imp.ext.prebid.storedAuctionResponse.seatBidObject.bid.flatten().sort()

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    def "PBS should prefer seatbidarr from request over seatbidobj from imp when both are present"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        bidRequest.tap{
            imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse().tap {
                seatBidObject = SeatBid.getStoredResponse(bidRequest)
            }
            ext.prebid.storedAuctionResponse = new StoredAuctionResponse(seatBids: [storedAuctionResponse])
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain same stored auction response as requested"
        assert response.seatbid == [storedAuctionResponse]

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(bidRequest.id) == 0
    }

    private static final Imp getImpWithSeatBidObject() {
        def imp = Imp.defaultImpression
        def bids = Bid.getDefaultBids([imp])
        def seatBid = new SeatBid(bid: bids, seat: GENERIC)
        imp.tap {
            ext.prebid.storedAuctionResponse = new StoredAuctionResponse(seatBidObject: seatBid)
        }
    }

    private static final List<SeatBid> convertToComparableSeatBid(List<SeatBid> seatBid) {
        seatBid*.tap {
            it.bid*.ext = null
            it.group = null
        }
    }
}
