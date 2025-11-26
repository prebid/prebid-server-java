package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.Targeting
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Format
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.VideoPlacementSubtypes
import org.prebid.server.functional.model.request.auction.VideoPlcmtSubtype
import org.prebid.server.functional.model.request.get.GeneralGetRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.MediaType
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import java.nio.charset.StandardCharsets

import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class GeneralGetInterfaceImpSpec extends BaseSpec {

    def "PBS should apply mimes from general get request when it's specified"() {
        given: "Default General get request"
        def mimes = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.mimes = [mimes]
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain mimes from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        assert bidderRequest.imp.first.singleMediaTypeData.mimes == [mimes]

        where:
        impMediaType << [MediaType.BANNER, MediaType.VIDEO, MediaType.AUDIO]
    }

    @PendingFeature
    def "PBS should remove invalid mimes from general get request when it's specified"() {
        given: "Default General get request"
        def validMemis = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.mimes = (invalidMimes + validMemis)
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors

        and: "Response should contain warning"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message == ['some message'] //TODO replace

        and: "Bidder request should contain mimes from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        assert bidderRequest.imp.first.singleMediaTypeData.mimes == [validMemis]

        where:
        invalidMimes << [[''], [PBSUtils.randomNumber.toString()]]
    }

    def "PBS should apply width and height for banner imp from general get request when it's specified"() {
        given: "Default General get request"
        def width = PBSUtils.randomNumber
        def height = PBSUtils.randomNumber
        def generalGetRequest = (bannerGeneralRequest(height, width) as GeneralGetRequest).tap {
            it.storedRequestId = PBSUtils.randomNumber
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        verifyAll(bidderRequest.imp.first.banner) {
            it.width == width
            it.height == height
            it.format.width == [width]
            it.format.height == [height]
        }

        where:
        bannerGeneralRequest <<
                [
                        { Integer h, Integer w -> new GeneralGetRequest(height: h, width: w) },
                        { Integer h, Integer w -> new GeneralGetRequest(overrideHeight: h, overrideWidth: w) },
                        { Integer h, Integer w ->
                            new GeneralGetRequest(height:  PBSUtils.randomNumber, width: PBSUtils.randomNumber,
                                    overrideHeight: h, overrideWidth: w)
                        }
                ]
    }

    def "PBS should unsuccessfully pass and throw error due to validation banner.{w.h} when values {w.h} is null"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.width = bannerFormatWidth
            it.height = bannerFormatHeight
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER).tap { it.banner.format = null }]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "PBs should throw error due to invalid request"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == 'Invalid request format: request.imp[0].banner has no sizes. Define "w" and "h", or include "format" elements'

        where:
        bannerFormatWidth             | bannerFormatHeight
        0                             | null
        null                          | null
        null                          | PBSUtils.randomNegativeNumber
    }

    def "PBS should unsuccessfully pass and throw error due to validation banner.{w.h} when values {w.h} not null"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.width = bannerFormatWidth
            it.height = bannerFormatHeight
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER).tap { it.banner.format = null }]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "PBs should throw error due to invalid request"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == 'Invalid request format: request.imp[0].banner must define a valid "h" and "w" properties'

        where:
        bannerFormatWidth             | bannerFormatHeight
        0                             | 0
        0                             | PBSUtils.randomNegativeNumber
        PBSUtils.randomNegativeNumber | PBSUtils.randomNegativeNumber
    }

    def "PBS should apply width and height for banner imp from general get request when banner width and height are square dimensions"() {
        given: "Default General get request"
        def side = PBSUtils.getRandomNumber(1, 10)
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.width = side
            it.height = side
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        verifyAll(bidderRequest.imp.first.banner) {
            it.width == side
            it.height == side
            it.format.width == [side]
            it.format.height == [side]
        }
    }

    def "PBS should apply width and height for video imp from general get request when it's specified"() {
        given: "Default General get request"
        def width = PBSUtils.randomNumber
        def height = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.width = width
            it.height = height
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.VIDEO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        verifyAll(bidderRequest.imp.first.video) {
            it.width == width
            it.height == height
        }
    }

    def "PBS should apply sizes banner imp from general get request when it's specified"() {
        given: "Default General get request"
        def width = PBSUtils.randomNumber
        def height = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.sizes = ["${width}x${height}".toString()]
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        assert bidderRequest.imp.first.banner.format.width == [width]
        assert bidderRequest.imp.first.banner.format.height == [height]
    }

    def "PBS should apply sizes legacy banner imp from general get request when it's specified"() {
        given: "Default General get request"
        def width = PBSUtils.randomNumber
        def height = PBSUtils.randomNumber
        def widthSecond = PBSUtils.randomNumber
        def heightSecond = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.sizesLegacy = ["${width}x${height},${widthSecond}x${heightSecond}".toString()]
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        assert bidderRequest.imp.first.banner.format.width == [width, widthSecond]
        assert bidderRequest.imp.first.banner.format.height == [height, heightSecond]
    }

    def "PBS should apply sizes banner imp from general get request when banner width and height are square dimensions"() {
        given: "Default General get request"
        def side = PBSUtils.getRandomNumber(1, 10)
        def generalGetRequest = (bannerGeneralRequest(side) as GeneralGetRequest).tap {
            it.storedRequestId = PBSUtils.randomNumber
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        assert bidderRequest.imp.first.banner.format.width == [side]
        assert bidderRequest.imp.first.banner.format.height == [side]

        where:
        bannerGeneralRequest <<
                [
                        { Integer requestSide -> new GeneralGetRequest(sizes: ["${requestSide}x${requestSide}".toString()]) },
                        { Integer requestSide -> new GeneralGetRequest(sizesLegacy: ["${requestSide}x${requestSide}".toString()]) },
                        { Integer requestSide ->
                            new GeneralGetRequest(sizes: ["${requestSide}x${requestSide}".toString()],
                                    sizesLegacy: ["${PBSUtils.randomNumber}x${PBSUtils.randomNumber}".toString()])
                        }
                ]
    }

    def "PBS should ignore sizes banner imp from general get request when it's specified partially"() {
        given: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        assert bidderRequest.imp.banner.format.width == request.imp.banner.format.width
        assert bidderRequest.imp.banner.format.height == request.imp.banner.format.height

        where:
        generalGetRequest << [
                GeneralGetRequest.default.tap { sizes = ['0x'] },
                GeneralGetRequest.default.tap { sizes = ['x'] },
                GeneralGetRequest.default.tap { sizes = ['0'] },
                GeneralGetRequest.default.tap { sizes = [''] },
                GeneralGetRequest.default.tap { sizes = [' '] },
                GeneralGetRequest.default.tap { sizes = [null] },
                GeneralGetRequest.default.tap { sizes = ["${PBSUtils.randomNegativeNumber}x".toString()] },
        ]
    }

    def "PBS should unsuccessfully pass and throw error due to validation banner sizes when sizes width and height is invalid"() {
        given: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "PBs should throw error due to invalid request"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == 'Invalid request format: request.imp[0].banner.format[0] should define *either* ' +
                '{w, h} (for static size requirements) *or* {wmin, wratio, hratio} (for flexible sizes) to be non-zero positive'

        where:
        generalGetRequest << [
                GeneralGetRequest.default.tap { sizes = ['0x0'] },
                GeneralGetRequest.default.tap { sizes = ["0x${PBSUtils.randomNegativeNumber}".toString()] },
                GeneralGetRequest.default.tap { sizes = ["x${PBSUtils.randomNegativeNumber}".toString()] },
                GeneralGetRequest.default.tap { sizes = ["${PBSUtils.randomNegativeNumber}x${PBSUtils.randomNegativeNumber}".toString()] },
        ]
    }

    def "PBS should apply slot from height general get request when it's specified"() {
        given: "Default General get request"
        def slotParam = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.slot = slotParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain tagId from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.tagId.flatten() == [slotParam]
    }

    def "PBS should apply duration from general get request when it's specified"() {
        given: "Default General get request"
        def minDurationParam = PBSUtils.randomNumber
        def maxDurationParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.minDuration = minDurationParam
            it.maxDuration = maxDurationParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain mimes from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.singleMediaTypeData.minduration == [minDurationParam]
        assert bidderRequest.imp.singleMediaTypeData.maxduration == [maxDurationParam]

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply api from general get request when it's specified"() {
        given: "Default General get request"
        def apiParam = [PBSUtils.randomNumber, PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.api = apiParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain api from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.api == apiParam

        where:
        impMediaType << [MediaType.BANNER, MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply battr from general get request when it's specified"() {
        given: "Default General get request"
        def battrParam = [PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.blockAttributes = battrParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain battr from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.battr == battrParam

        where:
        impMediaType << [MediaType.BANNER, MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply delivery from general get request when it's specified"() {
        given: "Default General get request"
        def deliveryParam = [PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.delivery = deliveryParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain delivery from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.delivery == deliveryParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply linearity from general get request when it's specified"() {
        given: "Default General get request"
        def linearityParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.linearity = linearityParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.VIDEO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain linearity from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.video.linearity == linearityParam
    }

    def "PBS should apply minbr and maxbr from general get request when it's specified"() {
        given: "Default General get request"
        def minBitrateParam = PBSUtils.randomNumber
        def maxBitrateParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.minBitrate = minBitrateParam
            it.maxBitrate = maxBitrateParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain minbr from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.minbitrate == minBitrateParam
        assert bidderRequest.imp.first.singleMediaTypeData.maxbitrate == maxBitrateParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply maxex from general get request when it's specified"() {
        given: "Default General get request"
        def maxexParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.maxExtended = maxexParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain maxex from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.maxextended == maxexParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply maxseq from general get request when it's specified"() {
        given: "Default General get request"
        def maxseqParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.maxSequence = maxseqParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain maxseq from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.maxseq == maxseqParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply minCpmPerSec from general get request when it's specified"() {
        given: "Default General get request"
        def mincpmsParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.minCpmPerSec = mincpmsParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain mincpms from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.mincpmpersec == mincpmsParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply poddur from general get request when it's specified"() {
        given: "Default General get request"
        def poddurParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.podDuration = poddurParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain poddur from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.poddur == poddurParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply podId from general get request when it's specified"() {
        given: "Default General get request"
        def podIdParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.podId = podIdParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain podid from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.podid == podIdParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply podseq from general get request when it's specified"() {
        given: "Default General get request"
        def podSequenceParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.podSequence = podSequenceParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain podseq from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.podseq == podSequenceParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply proto from general get request when it's specified"() {
        given: "Default General get request"
        def protoParam = [PBSUtils.randomNumber, PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.protocols = protoParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain proto from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.protocols == protoParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply rqddurs from general get request when it's specified"() {
        given: "Default General get request"
        def requiredDurationsParam = [PBSUtils.randomNumber, PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.requiredDurations = requiredDurationsParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain rqddurs from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.rqddurs == requiredDurationsParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply sequence from general get request when it's specified"() {
        given: "Default General get request"
        def sequenceParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.sequence = sequenceParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain seq from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.sequence == sequenceParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply slotInPod from general get request when it's specified"() {
        given: "Default General get request"
        def slotInPodParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.slotInPod = slotInPodParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain slotinpod from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.slotinpod == slotInPodParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply startdelay from general get request when it's specified"() {
        given: "Default General get request"
        def startDelayParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.startDelay = startDelayParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain startdelay from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.startdelay == startDelayParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply skip from general get request when it's specified"() {
        given: "Default General get request"
        def skipParam = PBSUtils.randomNumber
        def skipAfterParam = PBSUtils.randomNumber
        def skipMinParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.skip = skipParam
            it.skipAfter = skipAfterParam
            it.skipMin = skipMinParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.VIDEO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain skip from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        verifyAll(bidderRequest.imp.first.video) {
            it.skip == skipParam
            it.skipafter == skipAfterParam
            it.skipmin == skipMinParam
        }
    }

    def "PBS should apply position from general get request when it's specified"() {
        given: "Default General get request"
        def positionParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.position = positionParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(impMediaType)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain pos from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.singleMediaTypeData.pos == positionParam

        where:
        impMediaType << [MediaType.BANNER, MediaType.VIDEO]
    }

    def "PBS should apply stitched from general get request when it's specified"() {
        given: "Default General get request"
        def stitchedParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.stitched = stitchedParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.AUDIO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain stitched from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.audio.stitched == stitchedParam
    }

    def "PBS should apply feed from general get request when it's specified"() {
        given: "Default General get request"
        def feedParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.feed = feedParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.AUDIO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain feed from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.audio.feed == feedParam
    }

    def "PBS should apply normalizedVolume from general get request when it's specified"() {
        given: "Default General get request"
        def normalizedVolumeParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.normalizedVolume = normalizedVolumeParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.AUDIO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain nvol from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.audio.nvol == normalizedVolumeParam
    }

    def "PBS should apply placement from general get request when it's specified"() {
        given: "Default General get request"
        def placementParam = PBSUtils.getRandomEnum(VideoPlacementSubtypes)
        def placementSubtypeParam = PBSUtils.getRandomEnum(VideoPlcmtSubtype)
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.placement = placementParam
            it.placementSubtype = placementSubtypeParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.VIDEO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain placement from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.video.placement == placementParam
        assert bidderRequest.imp.first.video.plcmt == placementSubtypeParam
    }

    def "PBS should apply playbackendParam from general get request when it's specified"() {
        given: "Default General get request"
        def playbackEndParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.playbackEnd = playbackEndParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.VIDEO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain playbackmethod from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.video.playbackend == playbackEndParam
    }

    def "PBS should apply playbackMethodParam from general get request when it's specified"() {
        given: "Default General get request"
        def playbackMethodParam = [PBSUtils.randomNumber, PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.playbackMethods = playbackMethodParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.VIDEO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain playbackmethod from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.video.playbackmethod == playbackMethodParam
    }

    def "PBS should apply boxingAllowedParam from general get request when it's specified"() {
        given: "Default General get request"
        def boxingAllowedParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.boxingAllowed = boxingAllowedParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.VIDEO)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain boxingallowed from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.video.boxingallowed == boxingAllowedParam
    }

    def "PBS should apply btype from general get request when it's specified"() {
        given: "Default General get request"
        def btypeParam = [PBSUtils.randomNumber, PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.bannerTypes = btypeParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain btype from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.banner.btype == btypeParam
    }

    def "PBS should apply expandableDirections from general get request when it's specified"() {
        given: "Default General get request"
        def expandableDirectionsParam = [PBSUtils.randomNumber, PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.expandableDirections = expandableDirectionsParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain expdir from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.banner.expdir == expandableDirectionsParam
    }

    def "PBS should apply topFrame from general get request when it's specified"() {
        given: "Default General get request"
        def topFrameParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.topFrame = topFrameParam
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [Imp.getDefaultImpression(MediaType.BANNER)]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain topFrame from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.banner.topframe == topFrameParam
    }

    def "PBS should use original values for banner imp when it's not specified in get request"() {
        given: "Default General get request"
        def storedRequestId = PBSUtils.randomString
        def generalGetRequest = new GeneralGetRequest(storedRequestId: storedRequestId)

        and: "Default stored request"
        def bannerImp = Imp.getDefaultImpression(MediaType.BANNER).tap {
            it.banner.mimes = [PBSUtils.randomString]
            it.banner.width = PBSUtils.randomNumber
            it.banner.height = PBSUtils.randomNumber
            it.banner.format = [new Format(width: PBSUtils.randomNumber, height: PBSUtils.randomNumber)]
            it.banner.api = [PBSUtils.randomNumber]
            it.banner.battr = [PBSUtils.randomNumber]
            it.banner.pos = PBSUtils.randomNumber
            it.banner.btype = [PBSUtils.randomNumber]
            it.banner.expdir = [PBSUtils.randomNumber]
            it.banner.topframe = PBSUtils.randomNumber
        }
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [bannerImp]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain imp data from original request"
        verifyAll(bidder.getBidderRequest(request.id).imp.first.banner) {
            it.mimes == bannerImp.banner.mimes
            it.width == bannerImp.banner.width
            it.height == bannerImp.banner.height
            it.format == bannerImp.banner.format
            it.api == bannerImp.banner.api
            it.battr == bannerImp.banner.battr
            it.pos == bannerImp.banner.pos
            it.btype == bannerImp.banner.btype
            it.expdir == bannerImp.banner.expdir
            it.topframe == bannerImp.banner.topframe
        }
    }

    def "PBS should use original values for video imp when it's not specified in get request"() {
        given: "Default General get request"
        def storedRequestId = PBSUtils.randomString
        def generalGetRequest = new GeneralGetRequest(storedRequestId: storedRequestId)

        and: "Default stored request"
        def videoImp = Imp.getDefaultImpression(MediaType.VIDEO).tap {
            it.video.mimes = [PBSUtils.randomString]
            it.video.width = PBSUtils.randomNumber
            it.video.height = PBSUtils.randomNumber
            it.video.minduration = PBSUtils.randomNumber
            it.video.maxduration = PBSUtils.randomNumber
            it.video.api = [PBSUtils.randomNumber]
            it.video.battr = [PBSUtils.randomNumber]
            it.video.delivery = [PBSUtils.randomNumber]
            it.video.linearity = PBSUtils.randomNumber
            it.video.minbitrate = PBSUtils.randomNumber
            it.video.maxbitrate = PBSUtils.randomNumber
            it.video.maxextended = PBSUtils.randomNumber
            it.video.maxseq = PBSUtils.randomNumber
            it.video.mincpmpersec = PBSUtils.randomNumber
            it.video.poddur = PBSUtils.randomNumber
            it.video.podid = PBSUtils.randomNumber
            it.video.podseq = PBSUtils.randomNumber
            it.video.protocols = [PBSUtils.randomNumber]
            it.video.rqddurs = [PBSUtils.randomNumber]
            it.video.sequence = PBSUtils.randomNumber
            it.video.slotinpod = PBSUtils.randomNumber
            it.video.startdelay = PBSUtils.randomNumber
            it.video.skip = PBSUtils.randomNumber
            it.video.skipafter = PBSUtils.randomNumber
            it.video.skipmin = PBSUtils.randomNumber
            it.video.pos = PBSUtils.randomNumber
            it.video.placement = PBSUtils.getRandomEnum(VideoPlacementSubtypes)
            it.video.plcmt = PBSUtils.getRandomEnum(VideoPlcmtSubtype)
            it.video.playbackend = PBSUtils.randomNumber
            it.video.playbackmethod = [PBSUtils.randomNumber]
            it.video.boxingallowed = PBSUtils.randomNumber
        }
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [videoImp]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain imp data from original request"
        verifyAll(bidder.getBidderRequest(request.id).imp.first.video) {
            it.mimes == videoImp.video.mimes
            it.width == videoImp.video.width
            it.height == videoImp.video.height
            it.minduration == videoImp.video.minduration
            it.maxduration == videoImp.video.maxduration
            it.api == videoImp.video.api
            it.battr == videoImp.video.battr
            it.delivery == videoImp.video.delivery
            it.linearity == videoImp.video.linearity
            it.minbitrate == videoImp.video.minbitrate
            it.maxbitrate == videoImp.video.maxbitrate
            it.maxextended == videoImp.video.maxextended
            it.maxseq == videoImp.video.maxseq
            it.mincpmpersec == videoImp.video.mincpmpersec
            it.poddur == videoImp.video.poddur
            it.podid == videoImp.video.podid
            it.podseq == videoImp.video.podseq
            it.protocols == videoImp.video.protocols
            it.rqddurs == videoImp.video.rqddurs
            it.sequence == videoImp.video.sequence
            it.slotinpod == videoImp.video.slotinpod
            it.startdelay == videoImp.video.startdelay
            it.skip == videoImp.video.skip
            it.skipafter == videoImp.video.skipafter
            it.skipmin == videoImp.video.skipmin
            it.pos == videoImp.video.pos
            it.placement == videoImp.video.placement
            it.plcmt == videoImp.video.plcmt
            it.playbackend == videoImp.video.playbackend
            it.playbackmethod == videoImp.video.playbackmethod
            it.boxingallowed == videoImp.video.boxingallowed
        }
    }

    def "PBS should use original values for audio imp when it's not specified in get request"() {
        given: "Default General get request"
        def storedRequestId = PBSUtils.randomString
        def generalGetRequest = new GeneralGetRequest(storedRequestId: storedRequestId)

        and: "Default stored request"
        def audioImp = Imp.getDefaultImpression(MediaType.AUDIO).tap {
            it.audio.mimes = [PBSUtils.randomString]
            it.audio.minduration = PBSUtils.randomNumber
            it.audio.maxduration = PBSUtils.randomNumber
            it.audio.api = [PBSUtils.randomNumber]
            it.audio.battr = [PBSUtils.randomNumber]
            it.audio.delivery = [PBSUtils.randomNumber]
            it.audio.minbitrate = PBSUtils.randomNumber
            it.audio.maxbitrate = PBSUtils.randomNumber
            it.audio.maxextended = PBSUtils.randomNumber
            it.audio.maxseq = PBSUtils.randomNumber
            it.audio.mincpmpersec = PBSUtils.randomNumber
            it.audio.poddur = PBSUtils.randomNumber
            it.audio.podid = PBSUtils.randomNumber
            it.audio.podseq = PBSUtils.randomNumber
            it.audio.protocols = [PBSUtils.randomNumber]
            it.audio.rqddurs = [PBSUtils.randomNumber]
            it.audio.sequence = PBSUtils.randomNumber
            it.audio.slotinpod = PBSUtils.randomNumber
            it.audio.startdelay = PBSUtils.randomNumber
            it.audio.stitched = PBSUtils.randomNumber
            it.audio.feed = PBSUtils.randomNumber
            it.audio.nvol = PBSUtils.randomNumber
        }
        def request = BidRequest.getDefaultBidRequest().tap {
            it.imp = [audioImp]
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain imp data from original request"
        verifyAll(bidder.getBidderRequest(request.id).imp.first.audio) {
            it.mimes == audioImp.audio.mimes
            it.minduration == audioImp.audio.minduration
            it.maxduration == audioImp.audio.maxduration
            it.api == audioImp.audio.api
            it.battr == audioImp.audio.battr
            it.delivery == audioImp.audio.delivery
            it.minbitrate == audioImp.audio.minbitrate
            it.maxbitrate == audioImp.audio.maxbitrate
            it.maxextended == audioImp.audio.maxextended
            it.maxseq == audioImp.audio.maxseq
            it.mincpmpersec == audioImp.audio.mincpmpersec
            it.poddur == audioImp.audio.poddur
            it.podid == audioImp.audio.podid
            it.podseq == audioImp.audio.podseq
            it.protocols == audioImp.audio.protocols
            it.rqddurs == audioImp.audio.rqddurs
            it.sequence == audioImp.audio.sequence
            it.slotinpod == audioImp.audio.slotinpod
            it.startdelay == audioImp.audio.startdelay
            it.stitched == audioImp.audio.stitched
            it.feed == audioImp.audio.feed
            it.nvol == audioImp.audio.nvol
        }
    }

    def "PBS get request should move targeting key to imp.ext.data"() {
        given: "Create targeting"
        def targeting = new Targeting().tap {
            any = PBSUtils.randomString
        }

        and: "Encode Targeting to String"
        def encodeTargeting = URLEncoder.encode(encode(targeting), StandardCharsets.UTF_8)

        and: "Amp request with targeting"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.targeting = encodeTargeting
        }

        and: "Default BidRequest"
        def bidRequest = BidRequest.defaultBidRequest

        and: "Create and save stored request into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.storedRequestId, bidRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Amp response should contain value from targeting in imp.ext.data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.imp[0].ext.data.any == targeting.any
    }

    def "PBS should throw exception when general get request linked to stored request with several imps"() {
        given: "Stored request with several imps"
        def request = BidRequest.getDefaultBidRequest().tap {
            addImp(Imp.defaultImpression)
            setAccountId(accountId)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "PBs should throw error due to invalid request"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == 400
        assert exception.responseBody == "data for tag_id '${generalGetRequest.resolveStoredRequestId()}' includes '${request.imp.size()}'" +
                " imp elements. Only one is allowed"

        where:
        generalGetRequest << [
                new GeneralGetRequest(storedRequestId: PBSUtils.randomNumber),
                new GeneralGetRequest(storedRequestIdLegacy: PBSUtils.randomNumber)
        ]
    }
}
