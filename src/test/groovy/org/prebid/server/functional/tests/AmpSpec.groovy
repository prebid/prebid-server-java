package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Shared

import static java.math.RoundingMode.DOWN
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

    def "PBS should return version in response header for #description"() {
        given: "Default AmpRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest
        ampStoredRequest.site.publisher.id = ampRequest.account

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

    def "PBS should return info from the stored response when it's defined in the stored request"() {
        given: "Default AMP request"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Default stored request with specified stored response"
        def storedResponseId = PBSUtils.randomNumber
        def ampStoredRequest = BidRequest.defaultStoredRequest
        ampStoredRequest.imp[0].ext.prebid.storedAuctionResponse = new StoredAuctionResponse(id: storedResponseId)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Stored response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(ampStoredRequest)
        def storedResponse = new StoredResponse(resid: storedResponseId, storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain information from stored response"
        def price = storedAuctionResponse.bid[0].price
        assert response.targeting["hb_pb"] == "${price.setScale(1, DOWN)}0"
        assert response.targeting["hb_size"] == "${storedAuctionResponse.bid[0].w}x${storedAuctionResponse.bid[0].h}"

        and: "PBS not send request to bidder"
        assert bidder.getRequestCount(ampStoredRequest.id) == 0
    }

    def "PBS should prefer parameters from the request when stored request is specified"() {
        given: "AMP request"
        def msW = PBSUtils.randomNumber
        def msH = PBSUtils.randomNumber
        def ampRequest = new AmpRequest().tap {
            tagId = PBSUtils.randomString
            curl = PBSUtils.randomString
            account = PBSUtils.randomNumber
            w = PBSUtils.randomNumber
            h = PBSUtils.randomNumber
            ms = "${msW}x${msH}"
            slot = PBSUtils.randomString
            gdprApplies = false
        }

        and: "Default stored request with specified: gdpr, debug"
        def ampStoredRequest = BidRequest.defaultStoredRequest
        ampStoredRequest.regs.ext.gdpr = 1

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.site?.page == ampRequest.curl
        assert bidderRequest.site?.publisher?.id == ampRequest.account.toString()
        assert bidderRequest.imp[0]?.tagid == ampRequest.slot
        assert bidderRequest.imp[0]?.banner?.format*.h == [ampRequest.h, msH]
        assert bidderRequest.imp[0]?.banner?.format*.w == [ampRequest.w, msW]
        assert bidderRequest.regs?.ext?.gdpr == (ampRequest.gdprApplies ? 1 : 0)
    }

    def "PBS should prefer ow,oh from the request when ads sizes specified in stored request"() {
        given: "AMP request"
        def ampRequest = new AmpRequest().tap {
            tagId = PBSUtils.randomString
            ow = PBSUtils.randomNumber
            oh = PBSUtils.randomNumber
        }

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.imp[0]?.banner?.format*.h == [ampRequest.oh]
        assert bidderRequest.imp[0]?.banner?.format*.w == [ampRequest.ow]
    }

    def "PBS should take parameters from the stored request when it's not specified in the request"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.site?.page == ampStoredRequest.site.page
        assert bidderRequest.site?.publisher?.id == ampStoredRequest.site.publisher.id
        assert !bidderRequest.imp[0]?.tagid
        assert bidderRequest.imp[0]?.banner?.format[0]?.h == ampStoredRequest.imp[0].banner.format[0].h
        assert bidderRequest.imp[0]?.banner?.format[0]?.w == ampStoredRequest.imp[0].banner.format[0].w
        assert bidderRequest.regs?.ext?.gdpr == ampStoredRequest.regs.ext.gdpr
    }
}
