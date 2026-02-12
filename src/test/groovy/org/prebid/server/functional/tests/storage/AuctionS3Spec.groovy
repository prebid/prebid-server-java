package org.prebid.server.functional.tests.storage

import org.apache.http.HttpStatus
import org.prebid.server.functional.model.db.StoredImp
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.PrebidStoredRequest
import org.prebid.server.functional.model.request.auction.SecurityLevel
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.NoBidResponse
import org.prebid.server.functional.service.S3Service
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.apache.http.HttpStatus.SC_BAD_REQUEST
import static org.prebid.server.functional.model.response.BidderErrorCode.GENERIC

class AuctionS3Spec extends StorageBaseSpec {

    def "PBS auction should populate imp[0].secure depend which value in imp stored request from S3 service"() {
        given: "Default bid request"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                it.secure = null
            }
        }

        and: "Save storedImp into S3 service"
        def secureStoredRequest = PBSUtils.getRandomEnum(SecurityLevel.class)
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impData = Imp.defaultImpression.tap {
                secure = secureStoredRequest
            }
        }
        s3Service.uploadStoredImp(DEFAULT_BUCKET, storedImp)

        when: "Requesting PBS auction"
        s3StoragePbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain imp[0].secure same value as in request"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].secure == secureStoredRequest
    }

    @PendingFeature
    def "PBS should throw exception when trying to populate imp[0].secure from imp stored request on S3 service with impId that doesn't matches"() {
        given: "Default bid request"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                it.secure = null
            }
        }

        and: "Save storedImp with different impId into S3 service"
        def secureStoredRequest = PBSUtils.getRandomNumber(0, 1)
        def storedImp = StoredImp.getStoredImp(bidRequest).tap {
            impId = PBSUtils.randomString
            impData = Imp.defaultImpression.tap {
                it.secure = secureStoredRequest
            }
        }
        s3Service.uploadStoredImp(DEFAULT_BUCKET, storedImp, storedRequestId)

        when: "Requesting PBS auction"
        def response = s3StoragePbsService.sendAuctionRequest(bidRequest, SC_BAD_REQUEST)

        then: "PBS should throw request format error"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR
        verifyAll(response.ext.errors[ErrorType.PREBID]) {
            it.code == [GENERIC]
            it.errorMassage == ["Invalid request format: Stored request processing failed: " +
                                        "No stored impression found for id: ${storedRequestId}"]
        }
    }

    def "PBS should throw exception when trying to populate imp[0].secure from invalid imp stored request on S3 service"() {
        given: "Default bid request"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                it.secure = null
            }
        }

        and: "Save storedImp into S3 service"
        s3Service.uploadFile(DEFAULT_BUCKET, INVALID_FILE_BODY, "${S3Service.DEFAULT_IMPS_DIR}/${storedRequestId}.json" )

        when: "Requesting PBS auction"
        def response = s3StoragePbsService.sendAuctionRequest(bidRequest, HttpStatus.SC_BAD_REQUEST)

        then: "PBS should throw request format error"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR
        verifyAll(response.ext.errors[ErrorType.PREBID]) {
            it.code == [GENERIC]
            it.errorMassage == ["Invalid request format: Stored request processing failed: " +
                                        "Can't parse Json for stored request with id ${storedRequestId}"]
        }
    }

    def "PBS should throw exception when trying to populate imp[0].secure from unexciting imp stored request on S3 service"() {
        given: "Default bid request"
        def storedRequestId = PBSUtils.randomString
        def bidRequest = BidRequest.defaultBidRequest.tap {
            imp[0].tap {
                it.ext.prebid.storedRequest = new PrebidStoredRequest(id: storedRequestId)
                it.secure = null
            }
        }

        when: "Requesting PBS auction"
        def response = s3StoragePbsService.sendAuctionRequest(bidRequest, HttpStatus.SC_BAD_REQUEST)

        then: "PBS should throw request format error"
        assert response.noBidResponse == NoBidResponse.UNKNOWN_ERROR
        verifyAll(response.ext.errors[ErrorType.PREBID]) {
            it.code == [GENERIC]
            it.errorMassage == ["Invalid request format: Stored request processing failed: " +
                                        "No stored impression found for id: ${storedRequestId}"]
        }
    }
}
