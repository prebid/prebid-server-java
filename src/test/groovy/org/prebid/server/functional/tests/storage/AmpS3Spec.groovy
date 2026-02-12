package org.prebid.server.functional.tests.storage

import org.apache.http.HttpStatus
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.S3Service
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.apache.http.HttpStatus.SC_BAD_REQUEST
import static org.prebid.server.functional.model.response.BidderErrorCode.GENERIC

class AmpS3Spec extends StorageBaseSpec {

    def "PBS should take parameters from the stored request on S3 service when it's not specified in the request"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString).tap {
            account = PBSUtils.randomNumber as String
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
            setAccountId(ampRequest.account)
        }

        and: "Stored request in S3 service"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        s3Service.uploadStoredRequest(DEFAULT_BUCKET, storedRequest)

        when: "PBS processes amp request"
        s3StoragePbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.site?.page == ampStoredRequest.site.page
        assert bidderRequest.site?.publisher?.id == ampStoredRequest.site.publisher.id
        assert !bidderRequest.imp[0]?.tagId
        assert bidderRequest.imp[0]?.banner?.format[0]?.height == ampStoredRequest.imp[0].banner.format[0].height
        assert bidderRequest.imp[0]?.banner?.format[0]?.width == ampStoredRequest.imp[0].banner.format[0].width
        assert bidderRequest.regs?.gdpr == ampStoredRequest.regs.gdpr
    }

    @PendingFeature
    def "PBS should throw exception when trying to take parameters from the stored request on S3 service with invalid id in file"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString).tap {
            account = PBSUtils.randomNumber as String
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
            setAccountId(ampRequest.account)
        }

        and: "Stored request in S3 service"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest).tap {
            it.requestId = PBSUtils.randomNumber
        }
        s3Service.uploadStoredRequest(DEFAULT_BUCKET, storedRequest, ampRequest.tagId)

        when: "PBS processes amp request"
        def response = s3StoragePbsService.sendAmpRequest(ampRequest, SC_BAD_REQUEST)

        then: "PBS should throw request format error"
        verifyAll(response.ext.errors[ErrorType.PREBID]) {
            it.code == [GENERIC]
            it.errorMassage == ["Invalid request format: Stored request processing failed: " +
                                        "No stored request found for id: ${ampRequest.tagId}"]
        }
    }

    def "PBS should throw exception when trying to take parameters from request where id isn't match with stored request id"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString).tap {
            account = PBSUtils.randomNumber as String
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
            setAccountId(ampRequest.account)
        }

        and: "Stored request in S3 service"
        s3Service.uploadFile(DEFAULT_BUCKET, INVALID_FILE_BODY, "${S3Service.DEFAULT_REQUEST_DIR}/${ampRequest.tagId}.json")

        when: "PBS processes amp request"
        def response = s3StoragePbsService.sendAmpRequest(ampRequest, HttpStatus.SC_BAD_REQUEST)

        then: "PBS should throw request format error"
        verifyAll(response.ext.errors[ErrorType.PREBID]) {
            it.code == [GENERIC]
            it.errorMassage == ["Invalid request format: Stored request processing failed: " +
                                        "Can't parse Json for stored request with id ${ampRequest.tagId}"]
        }
    }

    def "PBS should throw an exception when trying to take parameters from stored request on S3 service that do not exist"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString).tap {
            account = PBSUtils.randomNumber as String
        }

        when: "PBS processes amp request"
        def response = s3StoragePbsService.sendAmpRequest(ampRequest, HttpStatus.SC_BAD_REQUEST)

        then: "PBS should throw request format error"
        verifyAll(response.ext.errors[ErrorType.PREBID]) {
            it.code == [GENERIC]
            it.errorMassage == ["Invalid request format: Stored request processing failed: " +
                                        "No stored request found for id: ${ampRequest.tagId}"]
        }
    }
}
