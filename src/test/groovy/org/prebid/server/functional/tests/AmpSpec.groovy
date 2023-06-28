package org.prebid.server.functional.tests

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.db.StoredResponse
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.model.request.auction.StoredAuctionResponse
import org.prebid.server.functional.model.request.auction.User
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.model.response.auction.SeatBid
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfConsent

import static org.prebid.server.functional.util.SystemProperties.PBS_VERSION
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V3

class AmpSpec extends BaseSpec {

    def "PBS should return version in response header for #description"() {
        given: "Default AmpRequest"
        def ampStoredRequest = BidRequest.defaultBidRequest
        ampStoredRequest.site.publisher.id = ampRequest.account

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequestRaw(ampRequest)

        then: "Response header should contain PBS version"
        assert response.headers["x-prebid"] == "pbs-java/$PBS_VERSION"

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
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Stored response in DB"
        def storedAuctionResponse = SeatBid.getStoredResponse(ampStoredRequest)
        def storedResponse = new StoredResponse(responseId: storedResponseId,
                storedAuctionResponse: storedAuctionResponse)
        storedResponseDao.save(storedResponse)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain information from stored response"
        def price = storedAuctionResponse.bid[0].price
        assert response.targeting["hb_pb"] == getRoundedTargetingValueWithDefaultPrecision(price)
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
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.site?.page == ampRequest.curl
        assert bidderRequest.site?.publisher?.id == ampRequest.account.toString()
        assert bidderRequest.imp[0]?.tagId == ampRequest.slot
        assert bidderRequest.imp[0]?.banner?.format*.h == [ampRequest.h, msH]
        assert bidderRequest.imp[0]?.banner?.format*.w == [ampRequest.w, msW]
        assert bidderRequest.regs?.gdpr == (ampRequest.gdprApplies ? 1 : 0)
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
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
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
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)

        assert bidderRequest.site?.page == ampStoredRequest.site.page
        assert bidderRequest.site?.publisher?.id == ampStoredRequest.site.publisher.id
        assert !bidderRequest.imp[0]?.tagId
        assert bidderRequest.imp[0]?.banner?.format[0]?.h == ampStoredRequest.imp[0].banner.format[0].h
        assert bidderRequest.imp[0]?.banner?.format[0]?.w == ampStoredRequest.imp[0].banner.format[0].w
        assert bidderRequest.regs?.gdpr == ampStoredRequest.regs.ext.gdpr
    }

    def "PBS cookie sync with proper consent.tcfPolicyVersion parameter should process request and display metrics for tcf and gvl"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Default stored request with gpp"
        def gppConsent = new TcfConsent.Builder()
                .setTcfPolicyVersion(tcfPolicyVersion.value)
                .build()
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
            user = new User(consent: gppConsent)
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain parameters from the stored request"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequest.user.consent == gppConsent as String

        and: "Metric should contain tcf and gvl requests"
        def metric = defaultPbsService.sendCollectedMetricsRequest()
        assert metric["privacy.tcf.v2.requests"] == 1
        assert metric["privacy.gvl.v2.requests"] == 1

        where:
        tcfPolicyVersion << [TCF_POLICY_V2, TCF_POLICY_V3]
    }

    def "PBS cookie sync with invalid consent.tcfPolicyVersion parameter should reject request and update metrics"() {
        given: "AMP request"
        def ampRequest = new AmpRequest(tagId: PBSUtils.randomString)

        and: "Default stored request with tcf consent"
        def invalidTcfPolicyVersion = PBSUtils.getRandomNumber(5, 63)
        def tcfConsent = new TcfConsent.Builder()
                .setTcfPolicyVersion(invalidTcfPolicyVersion)
                .build()
        def ampStoredRequest = BidRequest.defaultStoredRequest.tap {
            site = Site.defaultSite
            user = new User(consent: tcfConsent)
        }

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message ==
                ["Parsing consent string: ${tcfConsent} failed. TCF policy version ${invalidTcfPolicyVersion} is not supported" as String]
    }
}
