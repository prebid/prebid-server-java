package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Imp
import org.prebid.server.functional.model.request.auction.VideoPlacementSubtypes
import org.prebid.server.functional.model.request.auction.VideoPlcmtSubtype
import org.prebid.server.functional.model.request.get.GeneralGetRequest
import org.prebid.server.functional.model.response.auction.BidResponse
import org.prebid.server.functional.model.response.auction.MediaType
import org.prebid.server.functional.util.PBSUtils

class GeneralGetInterfaceImpSpec extends BaseSpec {

    def "PBS should apply mimes from general get request when it's specified"() {
        given: "Default General get request"
        def mimes = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.mimes = [mimes]
        }

        "Default stored request"
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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).mimes == [mimes]

        where:
        impMediaType << [
                MediaType.BANNER,
                MediaType.VIDEO,
                MediaType.AUDIO
        ]
    }

    def "PBS should apply width and height for banner imp from general get request when it's specified"() {
        given: "Default General get request"
        def width = PBSUtils.randomNumber
        def height = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.width = width
            it.height = height
        }

        "Default stored request"
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

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        verifyAll(bidderRequest.imp.first.banner) {
            it.width == width
            it.height == height
            it.format.width == [width]
            it.format.height == [height]
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

        "Default stored request"
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

    def "PBS should apply ow and oh for banner imp from general get request when it's specified"() {
        given: "Default General get request"
        def width = PBSUtils.randomNumber
        def height = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.originalWidth = width
            it.originalHeight = height
        }

        "Default stored request"
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

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        verifyAll(bidderRequest.imp.first.banner) {
            it.width == width
            it.height == height
            it.format.width == [width]
            it.format.height == [height]
        }
    }

    def "PBS should apply sizes banner imp from general get request when it's specified"() {
        given: "Default General get request"
        def width = PBSUtils.randomNumber
        def height = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.sizes = ["${width}x${height}"]
        }

        "Default stored request"
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
            it.sizesLegacy = ["${width}x${height}, ${widthSecond}x${heightSecond}"]
        }

        "Default stored request"
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

        and: "Bidder request should contain width and height from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.size() == 1
        assert bidderRequest.imp.first.banner.format.width == [width, widthSecond]
        assert bidderRequest.imp.first.banner.format.height == [height, heightSecond]
    }

    def "PBS should apply slot from height general get request when it's specified"() {
        given: "Default General get request"
        def slotParam = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.slot = [slotParam]
        }

        "Default stored request"
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
        assert !response.ext?.warnings

        and: "Bidder request should contain tagId from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.tagId == [slotParam]
    }

    def "PBS should apply duration from general get request when it's specified"() {
        given: "Default General get request"
        def minDurationParam = PBSUtils.randomNumber
        def maxDurationParam = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.minDuration = minDurationParam
            it.maxDuration = maxDurationParam
        }

        "Default stored request"
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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).minduration == [minDurationParam]
        assert bidderRequest.imp.first.getProperty(impMediaType.value).maxduration == [maxDurationParam]

        where:
        impMediaType << [
                MediaType.VIDEO,
                MediaType.AUDIO
        ]
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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).api == apiParam

        where:
        impMediaType << [MediaType.BANNER, MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply battr from general get request when it's specified"() {
        given: "Default General get request"
        def battrParam = [PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.battr = battrParam
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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).battr == battrParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).delivery == deliveryParam

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

    def "PBS should apply minbr from general get request when it's specified"() {
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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).minbitrate == minBitrateParam
        assert bidderRequest.imp.first.getProperty(impMediaType.value).maxbitrate == maxBitrateParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).maxextended == maxexParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).maxseq == maxseqParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).mincpmpersec == mincpmsParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).poddur == poddurParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).podid == podIdParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).podseq == podSequenceParam

        where:
        impMediaType << [MediaType.VIDEO, MediaType.AUDIO]
    }

    def "PBS should apply proto from general get request when it's specified"() {
        given: "Default General get request"
        def protoParam = [PBSUtils.randomNumber, PBSUtils.randomNumber]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.proto = protoParam
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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).protocols == protoParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).rqddurs == requiredDurationsParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).sequence == sequenceParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).slotinpod == slotInPodParam

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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).startdelay == startDelayParam

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
            it.skipAfter = skipAfter
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
        assert bidderRequest.imp.first.getProperty(impMediaType.value).pos == positionParam

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

        and: "Bidder request should contain boxingallowed from param"
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

        and: "Bidder request should contain boxingallowed from param"
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

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

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
            it.expandableDirections = expandableDirections
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

        and: "Default bid response"
        def bidResponse = BidResponse.getDefaultBidResponse(request)
        bidder.setResponse(request.id, bidResponse)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain topFrame from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.imp.first.banner.topframe == topFrameParam
    }
}
