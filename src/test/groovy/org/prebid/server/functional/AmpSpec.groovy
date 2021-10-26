package org.prebid.server.functional

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Native
import org.prebid.server.functional.model.request.auction.Publisher
import org.prebid.server.functional.model.request.auction.Request
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.service.PrebidServerException
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared
import spock.lang.Unroll

import static org.prebid.server.functional.util.SystemProperties.PBS_VERSION

class AmpSpec extends BaseSpec {

    private static final int DEFAULT_TIMEOUT = getRandomTimeout()
    private static final String PBS_VERSION_HEADER = "pbs-java/$PBS_VERSION"

    @Shared
    PrebidServerService prebidServerService = pbsServiceFactory.getService(["auction.max-timeout-ms"    : MAX_TIMEOUT as String,
                                                                            "auction.default-timeout-ms": DEFAULT_TIMEOUT as String])

    def "PBS should apply timeout from stored request when it's not specified in the request"() {
        given: "Default AMP request without timeout"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            timeout = null
        }

        and: "Default stored request with timeout"
        def timeout = getRandomTimeout()
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            tmax = timeout
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain timeout from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.tmax == timeout as Long
    }

    @Unroll
    def "PBS should prefer timeout from the request when stored request timeout is #tmax"() {
        given: "Default AMP request with timeout"
        def timeout = getRandomTimeout()
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.timeout = timeout
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = tmax
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain timeout from the request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.tmax == timeout as Long

        where:
        tmax << [null, getRandomTimeout()]
    }

    @Unroll
    def "PBS should honor max timeout from the settings"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.timeout = ampRequestTimeout
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = storedRequestTimeout
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.tmax == MAX_TIMEOUT as Long

        where:
        ampRequestTimeout || storedRequestTimeout
        MAX_TIMEOUT + 1   || null
        null              || MAX_TIMEOUT + 1
        MAX_TIMEOUT + 1   || MAX_TIMEOUT + 1
    }

    def "PBS should honor default timeout"() {
        given: "Default AMP request without timeout"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            it.timeout = null
        }

        and: "Default stored request without timeout"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            it.tmax = null
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request timeout should correspond to the maximum from the settings"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.tmax == DEFAULT_TIMEOUT as Long
    }

    @Unroll
    def "PBS should return version in response header for #description"() {
        given: "Default AmpRequest"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequestRaw(ampRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] == PBS_VERSION_HEADER

        where:
        ampRequest                   || description
        AmpRequest.defaultAmpRequest || "valid AMP request"
        new AmpRequest()             || "invalid AMP request"
    }

    def "PBS should pass site.ext.amp = 1 when the amp request was called"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Bidder request site.ext.amp should correspond to 1"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.site?.ext?.amp == 1
    }

    def "PBS should prefer ow,oh,ms from the request when ads sizes specified in stored request"() {
        given: "AMP request"
        def msW = PBSUtils.randomNumber
        def msH = PBSUtils.randomNumber
        def ampRequest = new AmpRequest().tap {
            tagId = PBSUtils.randomString
            curl = PBSUtils.randomString
            w = PBSUtils.randomNumber
            h = PBSUtils.randomNumber
            ow = PBSUtils.randomNumber
            oh = PBSUtils.randomNumber
            ms = "${msW}x${msH}"
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.imp[0]?.banner?.format*.h == [ampRequest.oh, msH]
        assert bidderRequest.imp[0]?.banner?.format*.w == [ampRequest.ow, msW]
    }

    @Unroll
    def "PBS should prefer w,h,ms from the request when stored request is defined"() {
        given: "AMP request"
        def msW = PBSUtils.randomNumber
        def msH = PBSUtils.randomNumber
        def ampRequest = new AmpRequest().tap {
            tagId = PBSUtils.randomString
            curl = PBSUtils.randomString
            w = PBSUtils.randomNumber
            h = PBSUtils.randomNumber
            ms = "${msW}x${msH}"
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            imp[0].banner.format[0].h = storedRequestH as Integer
            imp[0].banner.format[0].w = storedRequestW as Integer
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.imp[0]?.banner?.format*.h == [ampRequest.h, msH]
        assert bidderRequest.imp[0]?.banner?.format*.w == [ampRequest.w, msW]

        where:
        storedRequestH        | storedRequestW
        PBSUtils.randomNumber | PBSUtils.randomNumber
        null                  | null
    }

    @Unroll
    def "PBS should prefer w,h from the request when stored request is defined"() {
        given: "AMP request"
        def ampRequest = new AmpRequest().tap {
            tagId = PBSUtils.randomString
            curl = PBSUtils.randomString
            w = PBSUtils.randomNumber
            h = PBSUtils.randomNumber
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            imp[0].banner.format[0].h = storedRequestH as Integer
            imp[0].banner.format[0].w = shoredRequestW as Integer
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.imp[0]?.banner?.format*.h == [ampRequest.h]
        assert bidderRequest.imp[0]?.banner?.format*.w == [ampRequest.w]

        where:
        storedRequestH        | shoredRequestW
        PBSUtils.randomNumber | PBSUtils.randomNumber
        null                  | null
    }

    def "PBS should emit error when account is empty in request"() {
        given: "Default AMP request without timeout"
        def ampRequest = new AmpRequest().tap {
            tagId = PBSUtils.randomString
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = new Site(publisher: new Publisher(id: PBSUtils.randomString))
        }

        and: "Save storedRequest into DB"
        def storedRequest = new StoredRequest(reqid: ampRequest.tagId, accountId: PBSUtils.randomNumber, requestData: ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody ==
                "Invalid request format: No stored request found for id: $ampRequest.tagId for account: $ampStoredRequest.site.publisher.id"
    }

    @Unroll
    def "PBS should emit error for unauthorized account id when settings.enforce-valid-account = true"() {
        given: "PBS with targeting configuration"
        def pbsService = pbsServiceFactory.getService(["settings.enforce-valid-account": "true"])

        and: "Default amp request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        pbsService.sendAmpRequest(ampRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody == "Unauthorized account id: $ampRequest.account"
    }

    @Unroll
    def "PBS should apply domain field by priority for amp request"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            curl = requestCurl
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.domain = domain
            site.page = page
            site.id = PBSUtils.randomString
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        prebidServerService.sendAmpRequest(ampRequest, ["Referer": referer])

        then: "Bidder request should contain correct domain"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.site?.domain == "priority"

        where:
        requestCurl         | domain                | page                  | referer
        "https://priority/" | PBSUtils.randomString | PBSUtils.randomString | PBSUtils.randomString
        null                | "priority"            | PBSUtils.randomString | PBSUtils.randomString
        null                | null                  | "https://priority/"   | PBSUtils.randomString
        null                | null                  | null                  | "https://priority/"
    }

    def "PBS should pass native request to bidder"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with native"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            imp[0].banner = null
            imp[0].nativeObj = new Native(request: Request.request)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain correct native.request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.imp[0].nativeObj.request == ampStoredRequest.imp[0].nativeObj.request
    }

    def "PBS should reject native request when request field isn't passed"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with native"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            imp[0].banner = null
            imp[0].nativeObj = new Native()
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody ==
                "Invalid request format: request.imp[0].native contains empty request value"
    }

    def "PBS should send native request when bidder doesn't support the 'native' media type"() {
        given: "PBS with bidder configuration"
        def pbsService = pbsServiceFactory.getService(["adapters.generic.meta-info.site-media-types": "banner"])

        and: "Default AmpRequest"
        //increased timeout for test stabilization
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            timeout = 3000
        }

        and: "Default stored request with native"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            imp[0].banner = null
            imp[0].nativeObj = new Native(request: Request.request)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = pbsService.sendAmpRequest(ampRequest)

        println mapper.encode(response.ext.errors)
        then: "PBS should send request to bidder"
        assert bidder.getRequestCount(ampStoredRequest.id) == 1
    }

    def "PBS should reject native request when asserts list is empty"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with native"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            imp[0].banner = null
            imp[0].nativeObj = new Native(request: new Request(context: 1, plcmttype: 1, assets: []))
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Request should fail with error"
        def exception = thrown(PrebidServerException)
        assert exception.responseBody ==
                "Invalid request format: request.imp[0].native.request.assets must be an array containing at least one object"
    }

    def "PBS should copy query params from request and pass to resolved request"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response amp data should match request data"
        assert response.ext?.debug?.resolvedrequest?.ext?.prebid?.amp?.data == ampRequest
    }
}
