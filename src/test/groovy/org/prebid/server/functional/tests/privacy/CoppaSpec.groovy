package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Data
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.UserExt
import org.prebid.server.functional.model.request.auction.UserExtData
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class CoppaSpec extends PrivacyBaseSpec {

    @PendingFeature
    def "PBS should add debug log for auction request when coppa = 0 was passed"() {
        given: "Default COPPA BidRequest"
        def bidRequest = bidRequestWithGeo.tap {
            regs.coppa = 0
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.coppa?.coppa == bidRequest.regs.coppa
            privacy.resolvedPrivacy?.coppa?.coppa == bidRequest.regs.coppa

            !privacy.originPrivacy?.tcf?.gdpr
            !privacy.originPrivacy?.tcf?.tcfConsentString
            !privacy.originPrivacy?.tcf?.tcfConsentVersion
            !privacy.originPrivacy?.tcf?.inEea
            !privacy.resolvedPrivacy?.tcf?.gdpr
            !privacy.resolvedPrivacy?.tcf?.tcfConsentString
            !privacy.resolvedPrivacy?.tcf?.tcfConsentVersion
            !privacy.resolvedPrivacy?.tcf?.inEea

            !privacy.originPrivacy?.ccpa?.usPrivacy
            !privacy.resolvedPrivacy?.ccpa?.usPrivacy

            privacy.privacyActionsPerBidder[GENERIC].isEmpty()

            privacy.errors?.isEmpty()
        }
    }

    @PendingFeature
    def "PBS should add debug log for auction request when coppa = 1 was passed"() {
        given: "Default COPPA BidRequest"
        def bidRequest = bidRequestWithGeo.tap {
            regs.coppa = 1
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.coppa?.coppa == bidRequest.regs.coppa
            privacy.resolvedPrivacy?.coppa?.coppa == bidRequest.regs.coppa

            privacy.privacyActionsPerBidder[GENERIC] ==
                    ["Geolocation and address were removed from request to bidder according to CCPA policy."]

            privacy.errors?.isEmpty()
        }
    }

    @PendingFeature
    def "PBS should add debug log for amp request when coppa = 0 was passed"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Save storedRequest into DB"
        def ampStoredRequest = bidRequestWithGeo.tap {
            regs.coppa = 0
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.coppa?.coppa == ampStoredRequest.regs.coppa
            privacy.resolvedPrivacy?.coppa?.coppa == ampStoredRequest.regs.coppa

            !privacy.originPrivacy?.tcf?.gdpr
            !privacy.originPrivacy?.tcf?.tcfConsentString
            !privacy.originPrivacy?.tcf?.tcfConsentVersion
            !privacy.originPrivacy?.tcf?.inEea
            !privacy.resolvedPrivacy?.tcf?.gdpr
            !privacy.resolvedPrivacy?.tcf?.tcfConsentString
            !privacy.resolvedPrivacy?.tcf?.tcfConsentVersion
            !privacy.resolvedPrivacy?.tcf?.inEea

            !privacy.originPrivacy?.ccpa?.usPrivacy
            !privacy.resolvedPrivacy?.ccpa?.usPrivacy

            privacy.privacyActionsPerBidder[GENERIC].isEmpty()

            privacy.errors?.isEmpty()
        }
    }

    @PendingFeature
    def "PBS should add debug log for amp request when coppa = 1 was passed"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Save storedRequest into DB"
        def ampStoredRequest = bidRequestWithGeo.tap {
            regs.coppa = 1
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.coppa?.coppa == ampStoredRequest.regs.coppa
            privacy.resolvedPrivacy?.coppa?.coppa == ampStoredRequest.regs.coppa

            privacy.privacyActionsPerBidder[GENERIC] ==
                    ["Geolocation and address were removed from request to bidder according to CCPA policy."]

            privacy.errors?.isEmpty()
        }
    }

    def "PBS shouldn't mask device and user fields for auction request when coppa = 0 was passed"() {
        given: "BidRequest with personal data"
        def bidRequest = bidRequestWithPersonalData.tap {
            regs.coppa = 0
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll (bidderRequest) {
            bidderRequest.device.didsha1 == bidRequest.device.didsha1
            bidderRequest.device.didmd5 == bidRequest.device.didmd5
            bidderRequest.device.dpidsha1 == bidRequest.device.dpidsha1
            bidderRequest.device.ifa == bidRequest.device.ifa
            bidderRequest.device.macsha1 == bidRequest.device.macsha1
            bidderRequest.device.macmd5 == bidRequest.device.macmd5
            bidderRequest.device.dpidmd5 == bidRequest.device.dpidmd5
            bidderRequest.device.ip == bidRequest.device.ip
            bidderRequest.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequest.device.geo.lat == bidRequest.device.geo.lat
            bidderRequest.device.geo.lon == bidRequest.device.geo.lon
            bidderRequest.device.geo.country == bidRequest.device.geo.country
            bidderRequest.device.geo.region == bidRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == bidRequest.device.geo.utcoffset
            bidderRequest.device.geo.metro == bidRequest.device.geo.metro
            bidderRequest.device.geo.city == bidRequest.device.geo.city
            bidderRequest.device.geo.zip == bidRequest.device.geo.zip
            bidderRequest.device.geo.accuracy == bidRequest.device.geo.accuracy
            bidderRequest.device.geo.ipservice == bidRequest.device.geo.ipservice
            bidderRequest.device.geo.ext == bidRequest.device.geo.ext

            bidderRequest.user.id == bidRequest.user.id
            bidderRequest.user.buyeruid == bidRequest.user.buyeruid
            bidderRequest.user.yob == bidRequest.user.yob
            bidderRequest.user.gender == bidRequest.user.gender
            bidderRequest.user.eids[0].source == bidRequest.user.eids[0].source
            bidderRequest.user.data == bidRequest.user.data
            bidderRequest.user.geo.lat == bidRequest.user.geo.lat
            bidderRequest.user.geo.lon == bidRequest.user.geo.lon
            bidderRequest.user.ext.data.buyeruid == bidRequest.user.ext.data.buyeruid
        }
    }

    def "PBS should mask device and user fields for auction request when coppa = 1 was passed"() {
        given: "BidRequest with personal data"
        def bidRequest = bidRequestWithPersonalData.tap {
            regs.coppa = 1
        }

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll (bidderRequest) {
            bidderRequest.device.ip == "43.77.114.0"
            bidderRequest.device.ipv6 == "af47:892b:3e98:b400::"
            bidderRequest.device.geo.lat == bidRequest.device.geo.lat.round(2)
            bidderRequest.device.geo.lon == bidRequest.device.geo.lon.round(2)

            bidderRequest.device.geo.country == bidRequest.device.geo.country
            bidderRequest.device.geo.region == bidRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == bidRequest.device.geo.utcoffset
        }

        and: "Bidder request should mask device personal data"
        verifyAll (bidderRequest.device) {
            !didsha1
            !didmd5
            !dpidsha1
            !ifa
            !macsha1
            !macmd5
            !dpidmd5
            !geo.metro
            !geo.city
            !geo.zip
            !geo.accuracy
            !geo.ipservice
            !geo.ext
        }

        and: "Bidder request should mask user personal data"
        verifyAll (bidderRequest.user) {
            !id
            !buyeruid
            !yob
            !gender
            !eids
            !data
            !geo
            !ext
            !eids
            !ext?.eids
        }
    }

    def "PBS shouldn't mask device and user fields for amp request when coppa = 0 was passed"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Save storedRequest into DB"
        def ampStoredRequest = bidRequestWithPersonalData.tap {
            regs.coppa = 0
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll (bidderRequest) {
            bidderRequest.device.didsha1 == ampStoredRequest.device.didsha1
            bidderRequest.device.didmd5 == ampStoredRequest.device.didmd5
            bidderRequest.device.dpidsha1 == ampStoredRequest.device.dpidsha1
            bidderRequest.device.ifa == ampStoredRequest.device.ifa
            bidderRequest.device.macsha1 == ampStoredRequest.device.macsha1
            bidderRequest.device.macmd5 == ampStoredRequest.device.macmd5
            bidderRequest.device.dpidmd5 == ampStoredRequest.device.dpidmd5
            bidderRequest.device.ip == ampStoredRequest.device.ip
            bidderRequest.device.ipv6 == "af47:892b:3e98:b49a::"
            bidderRequest.device.geo.lat == ampStoredRequest.device.geo.lat
            bidderRequest.device.geo.lon == ampStoredRequest.device.geo.lon
            bidderRequest.device.geo.country == ampStoredRequest.device.geo.country
            bidderRequest.device.geo.region == ampStoredRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == ampStoredRequest.device.geo.utcoffset
            bidderRequest.device.geo.metro == ampStoredRequest.device.geo.metro
            bidderRequest.device.geo.city == ampStoredRequest.device.geo.city
            bidderRequest.device.geo.zip == ampStoredRequest.device.geo.zip
            bidderRequest.device.geo.accuracy == ampStoredRequest.device.geo.accuracy
            bidderRequest.device.geo.ipservice == ampStoredRequest.device.geo.ipservice
            bidderRequest.device.geo.ext == ampStoredRequest.device.geo.ext

            bidderRequest.user.id == ampStoredRequest.user.id
            bidderRequest.user.buyeruid == ampStoredRequest.user.buyeruid
            bidderRequest.user.yob == ampStoredRequest.user.yob
            bidderRequest.user.gender == ampStoredRequest.user.gender
            bidderRequest.user.eids[0].source == ampStoredRequest.user.eids[0].source
            bidderRequest.user.data == ampStoredRequest.user.data
            bidderRequest.user.geo.lat == ampStoredRequest.user.geo.lat
            bidderRequest.user.geo.lon == ampStoredRequest.user.geo.lon
            bidderRequest.user.ext.data.buyeruid == ampStoredRequest.user.ext.data.buyeruid
        }
    }

    def "PBS should mask device and user fields for amp request when coppa = 1 was passed"() {
        given: "Default AmpRequest"
        def ampRequest = AmpRequest.defaultAmpRequest

        and: "Save storedRequest into DB"
        def ampStoredRequest = bidRequestWithPersonalData.tap {
            regs.coppa = 1
        }
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes auction request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll (bidderRequest) {
            bidderRequest.device.ip == "43.77.114.0"
            bidderRequest.device.ipv6 == "af47:892b:3e98:b400::"
            bidderRequest.device.geo.lat == ampStoredRequest.device.geo.lat.round(2)
            bidderRequest.device.geo.lon == ampStoredRequest.device.geo.lon.round(2)

            bidderRequest.device.geo.country == ampStoredRequest.device.geo.country
            bidderRequest.device.geo.region == ampStoredRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == ampStoredRequest.device.geo.utcoffset
        }

        and: "Bidder request should mask device personal data"
        verifyAll (bidderRequest.device) {
            !didsha1
            !didmd5
            !dpidsha1
            !ifa
            !macsha1
            !macmd5
            !dpidmd5
            !geo.metro
            !geo.city
            !geo.zip
            !geo.accuracy
            !geo.ipservice
            !geo.ext
        }

        and: "Bidder request should mask user personal data"
        verifyAll (bidderRequest.user) {
            !id
            !buyeruid
            !yob
            !gender
            !eids
            !data
            !geo
            !ext
            !eids
            !ext?.eids
        }
    }

    private static BidRequest getBidRequestWithPersonalData() {
        getBidRequestWithGeo().tap {
            setAccountId(accountId)
            ext.prebid.trace = VERBOSE
            device.tap {
                didsha1 = PBSUtils.randomString
                didmd5 = PBSUtils.randomString
                dpidsha1 = PBSUtils.randomString
                ifa = PBSUtils.randomString
                macsha1 = PBSUtils.randomString
                macmd5 = PBSUtils.randomString
                dpidmd5 = PBSUtils.randomString
            }
            user.tap {
                customdata = PBSUtils.randomString
                eids = [Eid.defaultEid]
                data = [new Data(name: PBSUtils.randomString)]
                buyeruid = PBSUtils.randomString
                yob = PBSUtils.randomNumber
                gender = PBSUtils.randomString
                geo = Geo.FPDGeo
                ext = new UserExt(data: new UserExtData(buyeruid: PBSUtils.randomString))
            }
        }
    }
}
