package org.prebid.server.functional.tests.storage

import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.service.S3Service
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.apache.http.HttpStatus.SC_BAD_REQUEST
import static org.prebid.server.functional.model.response.BidderErrorCode.GENERIC
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.model.response.auction.NoBidResponse.UNKNOWN_ERROR

class StoredResponseS3Spec extends StorageBaseSpec {

    def "PBS should return info from S3 stored auction response when it defined in request"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)

        and: "Stored auction response in S3 storage"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId,
                storedAuctionResponse: storedAuctionResponse)
        s3Service.uploadStoredResponse(DEFAULT_BUCKET, storedResponse)

        when: "PBS processes auction request"
        def response = s3StoragePbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain information from stored auction response"
        assert response.id == bidRequest.id
        assert response.seatbid[0]?.seat == storedAuctionResponse.seat
        assert response.seatbid[0]?.bid?.size() == storedAuctionResponse.bid.size()
        assert response.seatbid[0]?.bid[0]?.impid == storedAuctionResponse.bid[0].impid
        assert response.seatbid[0]?.bid[0]?.price == storedAuctionResponse.bid[0].price
        assert response.seatbid[0]?.bid[0]?.id == storedAuctionResponse.bid[0].id

        and: "PBS not send request to bidder"
        assert !bidder.getRequestCount(bidRequest.id)
    }

    @PendingFeature
    def "PBS should throw request format exception when stored auction response id isn't match with requested response id"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)

        and: "Stored auction response in S3 storage with different id"
        def storedAuctionResponse = SeatBid.getStoredResponse(bidRequest)
        def storedResponse = new StoredResponse(responseId: PBSUtils.randomNumber,
                storedAuctionResponse: storedAuctionResponse)
        s3Service.uploadStoredResponse(DEFAULT_BUCKET, storedResponse, storedResponseId as String)

        when: "PBS processes auction request"
        def response = s3StoragePbsService.sendAuctionRequest(bidRequest, SC_BAD_REQUEST)

        then: "PBS should throw request format error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [GENERIC]
            it.errorMassage == ["Invalid request format: Failed to fetch stored auction response for " +
                                       "impId = ${bidRequest.imp[0].id} and storedAuctionResponse id = ${storedResponseId}."]
        }
    }

    def "PBS should throw request format exception when invalid stored auction response defined in S3 storage"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)

        and: "Invalid stored auction response in S3 storage"
        s3Service.uploadFile(DEFAULT_BUCKET, INVALID_FILE_BODY, "${S3Service.DEFAULT_RESPONSE_DIR}/${storedResponseId}.json")

        when: "PBS processes auction request"
        def response = s3StoragePbsService.sendAuctionRequest(bidRequest, SC_BAD_REQUEST)

        then: "PBS should throw request format error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [GENERIC]
            it.errorMassage == ["Invalid request format: Can't parse Json for stored response with id ${storedResponseId}"]
        }
    }

    def "PBS should throw request format exception when stored auction response defined in request but not defined in S3 storage"() {
        given: "Default basic BidRequest with stored response"
        def bidRequest = BidRequest.defaultBidRequest
        def storedResponseId = PBSUtils.randomNumber
        bidRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)

        when: "PBS processes auction request"
        def response = s3StoragePbsService.sendAuctionRequest(bidRequest)

        then: "PBS should throw request format error"
        assert response.noBidResponse == UNKNOWN_ERROR
        verifyAll(response.ext.errors[PREBID]) {
            it.code == [GENERIC]
            it.errorMassage == ["Invalid request format: Failed to fetch stored auction response for " +
                                        "impId = ${bidRequest.imp[0].id} and storedAuctionResponse id = ${storedResponseId}."]
        }
    }
}
