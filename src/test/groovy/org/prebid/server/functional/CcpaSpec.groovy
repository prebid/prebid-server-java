package org.prebid.server.functional

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.PendingFeature

@PBSTest
class CcpaSpec extends BaseSpec {

    // TODO: extend ccpa test with actual fields that we should mask
    def "PBS should mask publisher info when privacy.ccpa.enabled = true in account config"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def validCcpa = "1YYY"
        bidRequest.regs.ext = new RegsExt(gdpr: 0, usPrivacy: validCcpa)
        def lat = PBSUtils.getFractionalRandomNumber(0, 90)
        def lon = PBSUtils.getFractionalRandomNumber(0, 90)
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: true)
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == PBSUtils.getRoundFractionalNumber(lat, 2)
        assert bidderRequests.device?.geo?.lon == PBSUtils.getRoundFractionalNumber(lon, 2)
    }

    // TODO: extend this ccpa test with actual fields that we should mask
    def "PBS should not mask publisher info when privacy.ccpa.enabled = false in account config"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def validCcpa = "1YYY"
        bidRequest.regs.ext = new RegsExt(gdpr: 0, usPrivacy: validCcpa)
        def lat = PBSUtils.getFractionalRandomNumber(0, 90)
        def lon = PBSUtils.getFractionalRandomNumber(0, 90)
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: false)
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == lat
        assert bidderRequests.device?.geo?.lon == lon
    }

    @PendingFeature
    def "PBS should add debug log for auction request when valid ccpa was passed"() {
        given: "Default basic generic BidRequest"
        def validCcpa = "1YYY"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext = new RegsExt(usPrivacy: validCcpa)
            device = new Device(geo: new Geo(lat: PBSUtils.getFractionalRandomNumber(0, 90), lon: PBSUtils.getFractionalRandomNumber(0, 90)))
        }

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.ccpa?.usPrivacy == validCcpa
            privacy.resolvedPrivacy?.ccpa?.usPrivacy == validCcpa

            !privacy.originPrivacy?.coppa?.coppa
            !privacy.resolvedPrivacy?.coppa?.coppa

            !privacy.originPrivacy?.tcf?.gdpr
            !privacy.originPrivacy?.tcf?.tcfConsentString
            !privacy.originPrivacy?.tcf?.tcfConsentVersion
            !privacy.originPrivacy?.tcf?.inEea
            !privacy.resolvedPrivacy?.tcf?.gdpr
            !privacy.resolvedPrivacy?.tcf?.tcfConsentString
            !privacy.resolvedPrivacy?.tcf?.tcfConsentVersion
            !privacy.resolvedPrivacy?.tcf?.inEea

            privacy.privacyActionsPerBidder[BidderName.GENERIC] ==
                    ["Geolocation was masked in request to bidder according to CCPA policy."]

            privacy.errors?.isEmpty()
        }
    }

    @PendingFeature
    def "PBS should add debug log for auction request when invalid ccpa was passed"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def invalidCcpa = "1ASD"
        bidRequest.regs.ext = new RegsExt(usPrivacy: invalidCcpa)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain error"
        assert !response.ext?.errors

        and: "Response should contain debug log with error"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.ccpa?.usPrivacy == invalidCcpa
            privacy.resolvedPrivacy?.ccpa?.usPrivacy == invalidCcpa

            privacy.privacyActionsPerBidder[BidderName.GENERIC].isEmpty()

            privacy.errors == ["CCPA consent $invalidCcpa has invalid format: us_privacy must specify 'N' " +
                                       "or 'n', 'Y' or 'y', '-' for the explicit notice" as String]
        }
    }

    @PendingFeature
    def "PBS should add debug log for amp request when valid ccpa was passed"() {
        given: "Default AmpRequest"
        def validCcpa = "1YYY"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            gdprConsent = validCcpa
            consentType = 3
        }

        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
            device = new Device(geo: new Geo(lat: PBSUtils.getFractionalRandomNumber(0, 90), lon: PBSUtils.getFractionalRandomNumber(0, 90)))
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.ccpa?.usPrivacy == validCcpa
            privacy.resolvedPrivacy?.ccpa?.usPrivacy == validCcpa

            !privacy.originPrivacy?.coppa?.coppa
            !privacy.resolvedPrivacy?.coppa?.coppa

            !privacy.originPrivacy?.tcf?.gdpr
            !privacy.originPrivacy?.tcf?.tcfConsentString
            !privacy.originPrivacy?.tcf?.tcfConsentVersion
            !privacy.originPrivacy?.tcf?.inEea
            !privacy.resolvedPrivacy?.tcf?.gdpr
            !privacy.resolvedPrivacy?.tcf?.tcfConsentString
            !privacy.resolvedPrivacy?.tcf?.tcfConsentVersion
            !privacy.resolvedPrivacy?.tcf?.inEea

            privacy.privacyActionsPerBidder[BidderName.GENERIC] ==
                    ["Geolocation was masked in request to bidder according to CCPA policy."]

            privacy.errors?.isEmpty()
        }
    }

    @PendingFeature
    def "PBS should add debug log for amp request when invalid ccpa was passed"() {
        given: "Default AmpRequest"
        def invalidCcpa = "1ASD"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            gdprConsent = invalidCcpa
            consentType = 3
        }

        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
            device = new Device(geo: new Geo(lat: PBSUtils.getFractionalRandomNumber(0, 90), lon: PBSUtils.getFractionalRandomNumber(0, 90)))
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should not contain error"
        assert !response.ext?.errors

        and: "Response should contain debug log with error"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.ccpa?.usPrivacy == invalidCcpa
            privacy.resolvedPrivacy?.ccpa?.usPrivacy == invalidCcpa

            privacy.privacyActionsPerBidder[BidderName.GENERIC].isEmpty()

            privacy.errors ==
                    ["Amp request parameter consent_string or gdpr_consent have invalid format: $invalidCcpa" as String]
        }
    }
}
