package org.prebid.server.functional.tests

import org.prebid.server.functional.model.bidderspecific.BidderRequest
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Content
import org.prebid.server.functional.model.request.auction.DebugCondition
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.DeviceType
import org.prebid.server.functional.model.request.auction.PublicCountryIp
import org.prebid.server.functional.model.request.get.GeneralGetRequest
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.util.PBSUtils

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import static org.prebid.server.functional.model.request.auction.DistributionChannel.APP
import static org.prebid.server.functional.model.request.auction.DistributionChannel.DOOH
import static org.prebid.server.functional.model.request.auction.DistributionChannel.SITE
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID

class GeneralGetInterfaceRequestSpec extends BaseSpec {

    def "PBS should process bid request from default general get request"() {
        given: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should be valid"
        assert bidder.getBidderRequest(request.id)

        where:
        generalGetRequest << [
                new GeneralGetRequest(storedRequestId: PBSUtils.randomNumber),
                new GeneralGetRequest(storedRequestIdLegacy: PBSUtils.randomNumber)
        ]
    }

    def "PBS should process bid request from default general get request "() {
        given: "General get request without stored request param"
        def generalGetRequest = new GeneralGetRequest()

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.storedRequestIdLegacy, request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "replace" //TODO replace
    }

    def "PBS should prioritise new storedRequest param over legacy when both presents"() {
        given: "General get request with new and old stored request param"
        def generalGetRequest = new GeneralGetRequest(storedRequestId: PBSUtils.randomNumber,
                storedRequestIdLegacy: PBSUtils.randomNumber)

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.storedRequestIdLegacy, request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Request should fail with an error"
        def exception = thrown(PrebidServerException)
        assert exception.statusCode == BAD_REQUEST.code()
        assert exception.responseBody == "replace" //TODO replace
    }

    def "PBS should apply accountId from general get request when it's specified"() {
        given: "Default stored request"
        def request = BidRequest.getDefaultBidRequest(distributionType)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain accountId from param"
        assert bidder.getBidderRequest(request.id).accountId == generalGetRequest.resolveAccountId()

        where:
        generalGetRequest                                                            | distributionType
        GeneralGetRequest.default.tap { it.accountId = PBSUtils.randomNumber }       | SITE
        GeneralGetRequest.default.tap { it.accountId = PBSUtils.randomNumber }       | APP
        GeneralGetRequest.default.tap { it.accountId = PBSUtils.randomNumber }       | DOOH
        GeneralGetRequest.default.tap { it.accountIdLegacy = PBSUtils.randomNumber } | SITE
        GeneralGetRequest.default.tap { it.accountIdLegacy = PBSUtils.randomNumber } | APP
        GeneralGetRequest.default.tap { it.accountIdLegacy = PBSUtils.randomNumber } | DOOH
    }

    def "PBS should prioritise new accountId param over legacy when both presents"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.accountId = PBSUtils.randomNumber
            it.accountIdLegacy = PBSUtils.randomNumber
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

        and: "Bidder request should contain accountId from param"
        assert bidder.getBidderRequest(request.id).accountId == generalGetRequest.accountId
    }

    def "PBS should apply tmax from general get request when it's specified"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.timeoutMax = PBSUtils.randomNumber
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

        and: "Bidder request should contain tmax from param"
        assert bidder.getBidderRequest(request.id).tmax == generalGetRequest.timeoutMax
    }

    def "PBS shouldn't apply tmax from general get request when it's specified lower then 100"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.timeoutMax = tmax
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should contain warnings"
        assert response.ext?.warnings[PREBID]*.message == ["replace"] //TODO replace

        and: "Response should not contain errors and warnings"
        assert !response.ext?.errors

        and: "Bidder request should contain tmax from param"
        assert bidder.getBidderRequest(request.id).tmax != generalGetRequest.timeoutMax

        where:
        tmax << [PBSUtils.randomNegativeNumber, PBSUtils.getRandomNumber(0, 100)]
    }

    def "PBS should apply debug from general get request when it's specified"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.debug = debugCondition
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

        and: "Bidder request should contain debug from param"
        assert bidder.getBidderRequest(request.id).ext.prebid.debug == debugCondition

        where:
        debugCondition << [DebugCondition.DISABLED, DebugCondition.ENABLED]
    }

    def "PBS should apply outputformat from general get request when it's specified"() {
        given: "Default General get request"
        def outputFormat = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.outputFormat = outputFormat
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

        and: "Bidder request should contain outputformat from param"
        assert bidder.getBidderRequest(request.id).ext.prebid.outputFormat == outputFormat
    }

    def "PBS should apply outputmodule from general get request when it's specified"() {
        given: "Default General get request"
        def outputModule = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.outputModule = outputModule
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

        and: "Bidder request should contain outputformat from param"
        assert bidder.getBidderRequest(request.id).ext.prebid.outputModule == outputModule
    }

    def "PBS should apply storedAuctionResponse from general get request when it's specified"() {
        given: "Default General get request"
        def storedAuctionResponse = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.storedAuctionResponseId = storedAuctionResponse
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

        and: "Bidder request should contain storedAuctionResponse from param"
        assert bidder.getBidderRequest(request.id).ext.prebid.storedAuctionResponse.id == storedAuctionResponse
    }

    def "PBS should apply dnt from general get request when it's specified"() {
        given: "Default General get request"
        def dnt = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.doNotTrack = dnt
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

        and: "Bidder request should contain gpc from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.device.dnt == dnt
    }

    def "PBS should apply lmt from general get request when it's specified"() {
        given: "Default General get request"
        def lmt = PBSUtils.randomNumber
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.limitAdTracking = lmt
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

        and: "Bidder request should contain gpc from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.device.lmt == lmt
    }

    def "PBS should apply bcat from general get request when it's specified"() {
        given: "Default General get request"
        def bcat = [PBSUtils.randomString]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.blockedCategories = bcat
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

        and: "Bidder request should contain bcat from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.bcat == bcat
    }

    def "PBS should apply badv from general get request when it's specified"() {
        given: "Default General get request"
        def badv = [PBSUtils.randomString]
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.blockedAdvertisers = badv
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

        and: "Bidder request should contain badv from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.badv == badv
    }

    def "PBS should apply page from general get request when it's specified"() {
        given: "Default General get request"
        def page = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.page = page
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

        and: "Bidder request should contain page from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.site.page == page
    }

    def "PBS should apply bundle from general app bundle request when it's specified"() {
        given: "Default General get request"
        def bundle = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.appBundle = bundle
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest(APP)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain app bundle from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.app.bundle == bundle
    }

    def "PBS should apply name from general app name request when it's specified"() {
        given: "Default General get request"
        def name = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.appName = name
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest(APP)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain app name from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.app.name == name
    }

    def "PBS should apply name from general app storeUrl request when it's specified"() {
        given: "Default General get request"
        def storeUrl = PBSUtils.randomString
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.storeUrl = storeUrl
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest(APP)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain app storeUrl from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.app.storeUrl == storeUrl
    }

    def "PBS should apply distribution data from general get request when it's specified"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.contentGenre = PBSUtils.randomString
            it.contentLanguage = PBSUtils.randomString
            it.contentRating = PBSUtils.randomString
            it.contentCategory = PBSUtils.randomNumber
            it.contentCategoryTaxonomy = [PBSUtils.randomNumber]
            it.contentTitle = PBSUtils.randomString
            it.contentUrl = PBSUtils.randomString
            it.contentLivestream = PBSUtils.randomString
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest(distributionType)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain contentGenre from param"
        verifyAll(getRequestContent(bidder.getBidderRequest(request.id))) {
            it.genre == generalGetRequest.contentGenre
            it.language == generalGetRequest.contentLanguage
            it.contentrating == generalGetRequest.contentRating
            it.cat == [generalGetRequest.contentCategory.toString()]
            it.cattax == generalGetRequest.contentCategoryTaxonomy // TODO discuss this issue
            it.title == generalGetRequest.contentTitle
            it.url == generalGetRequest.contentUrl
            it.livestream == generalGetRequest.contentLivestream
        }

        where:
        distributionType << [SITE, APP, DOOH]
    }

    def "PBS should apply series from general get request when it's specified"() {
        given: "Default General get request"
        def contentSeries = PBSUtils.randomString
        def generalGetRequest = (rawGeneralGetRequest as GeneralGetRequest).tap {
            it.storedAuctionResponseId = PBSUtils.randomString
        }

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest(distributionType)

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest)

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain contentGenre from param"
        assert getRequestContent(bidder.getBidderRequest(request.id)).series == contentSeries

        where:
        distributionType | rawGeneralGetRequest
        SITE             | { String series -> new GeneralGetRequest(contentSeries: series) }
        APP              | { String series -> new GeneralGetRequest(contentSeries: series) }
        DOOH             | { String series -> new GeneralGetRequest(contentSeries: series) }

        SITE             | { String series -> new GeneralGetRequest(contentSeriesAlias: series) }
        APP              | { String series -> new GeneralGetRequest(contentSeriesAlias: series) }
        DOOH             | { String series -> new GeneralGetRequest(contentSeriesAlias: series) }

        SITE             | { String series -> new GeneralGetRequest(contentSeries: series, contentSeriesAlias: PBSUtils.randomString) }
        APP              | { String series -> new GeneralGetRequest(contentSeries: series, contentSeriesAlias: PBSUtils.randomString) }
        DOOH             | { String series -> new GeneralGetRequest(contentSeries: series, contentSeriesAlias: PBSUtils.randomString) }
    }

    def "PBS should apply device ip info from general get request when it's specified"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.deviceIp = deviceIp
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

        and: "Bidder request should contain device ip from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert getDeviceIp(bidderRequest.device) == deviceIp

        where:
        deviceIp << [
                PBSUtils.getRandomEnum(PublicCountryIp).v4,
                PBSUtils.getRandomEnum(PublicCountryIp).v6
        ]
    }

    def "PBS should apply device params from general get request when it's specified"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.default.tap {
            it.deviceUa = PBSUtils.randomString
            it.deviceType = PBSUtils.getRandomEnum(DeviceType)
            it.deviceIfa = PBSUtils.randomString
            it.deviceIfaType = PBSUtils.randomString
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

        and: "Bidder request should contain device info from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        verifyAll(bidderRequest.device) {
            it.ua == generalGetRequest.deviceUa
            it.devicetype == generalGetRequest.deviceType
            it.ifa == generalGetRequest.deviceIfa
            it.ext.ifaType == generalGetRequest.deviceIfaType
        }
    }

    def "PBS should apply site page info from header of get request when it's specified"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.getDefault()

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def page = PBSUtils.randomString
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest, ["Referer": page])

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain device ip from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.site.page == page
    }

    def "PBS should apply site page info from #header header of get request when parameter is not specified"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.getDefault()

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def ua = PBSUtils.randomString
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest, [header: ua])

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain device ip from param"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert bidderRequest.device.ua == ua

        where:
        header << ["User-Agent", "X-Device-User-Agent"]
    }

    def "PBS should apply device ip info from #header header of get request when parameter is not specified"() {
        given: "Default General get request"
        def generalGetRequest = GeneralGetRequest.getDefault()

        and: "Default stored request"
        def request = BidRequest.getDefaultBidRequest()

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(generalGetRequest.resolveStoredRequestId(), request)
        storedRequestDao.save(storedRequest)

        when: "PBS processes general get request"
        def response = defaultPbsService.sendGeneralGetRequest(generalGetRequest, [(header): deviceIp])

        then: "Response should not contain errors and warnings"
        assert !response.ext?.errors
        assert !response.ext?.warnings

        and: "Bidder request should contain device ip from headers"
        def bidderRequest = bidder.getBidderRequest(request.id)
        assert getDeviceIp(bidderRequest.device) == deviceIp

        where:
        header            | deviceIp
        "X-Forwarded-For" | PBSUtils.getRandomEnum(PublicCountryIp).v4
        "X-Forwarded-For" | PBSUtils.getRandomEnum(PublicCountryIp).v6

        "X-Device-IP"     | PBSUtils.getRandomEnum(PublicCountryIp).v4
        "X-Device-IP"     | PBSUtils.getRandomEnum(PublicCountryIp).v6

        "X-Real-IP"       | PBSUtils.getRandomEnum(PublicCountryIp).v4
        "X-Real-IP"       | PBSUtils.getRandomEnum(PublicCountryIp).v6

        "True-Client-IP"  | PBSUtils.getRandomEnum(PublicCountryIp).v4
        "True-Client-IP"  | PBSUtils.getRandomEnum(PublicCountryIp).v6
    }

    static String getDeviceIp(Device device) {
        device.ip ?: device.ipv6
    }

    static Content getRequestContent(BidderRequest bidderRequest) {
        def distributionChannels = bidderRequest.getRequestDistributionChannels()

        if (distributionChannels.contains(SITE)) {
            return bidderRequest.site.content
        }

        if (distributionChannels.contains(APP)) {
            return bidderRequest.app.content
        }

        return bidderRequest.dooh.content
    }
}
