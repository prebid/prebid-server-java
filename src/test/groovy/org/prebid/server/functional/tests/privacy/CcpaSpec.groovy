package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.privacy.BogusConsent
import org.prebid.server.functional.util.privacy.CcpaConsent
import spock.lang.PendingFeature

import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED

@PBSTest
class CcpaSpec extends PrivacyBaseSpec {

    // TODO: extend ccpa test with actual fields that we should mask
    def "PBS should mask publisher info when privacy.ccpa.enabled = true in account config"() {
        given: "Default ccpa BidRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def bidRequest = getCcpaBidRequest(validCcpa)

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
        assert bidderRequests.device?.geo == maskGeo(bidRequest)
    }

    // TODO: extend this ccpa test with actual fields that we should mask
    def "PBS should not mask publisher info when privacy.ccpa.enabled = false in account config"() {
        given: "Default ccpa BidRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def bidRequest = getCcpaBidRequest(validCcpa)

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
        assert bidderRequests.device?.geo?.lat == bidRequest.device.geo.lat
        assert bidderRequests.device?.geo?.lon == bidRequest.device.geo.lon
    }

    @PendingFeature
    def "PBS should add debug log for auction request when valid ccpa was passed"() {
        given: "Default ccpa BidRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def bidRequest = getCcpaBidRequest(validCcpa)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.ccpa?.usPrivacy == validCcpa as String
            privacy.resolvedPrivacy?.ccpa?.usPrivacy == validCcpa as String

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
        given: "Default ccpa BidRequest"
        def invalidCcpa = new BogusConsent()
        def bidRequest = getCcpaBidRequest(invalidCcpa)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain error"
        assert !response.ext?.errors

        and: "Response should contain debug log with error"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.ccpa?.usPrivacy == invalidCcpa as String
            privacy.resolvedPrivacy?.ccpa?.usPrivacy == invalidCcpa as String

            privacy.privacyActionsPerBidder[BidderName.GENERIC].isEmpty()

            privacy.errors == ["CCPA consent $invalidCcpa has invalid format: us_privacy must specify 'N' " +
                                       "or 'n', 'Y' or 'y', '-' for the explicit notice" as String]
        }
    }

    @PendingFeature
    def "PBS should add debug log for amp request when valid ccpa was passed"() {
        given: "Default AmpRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def ampRequest = getCcpaAmpRequest(validCcpa)

        and: "Save storedRequest into DB"
        def ampStoredRequest = bidRequestWithGeo
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.ccpa?.usPrivacy == validCcpa as String
            privacy.resolvedPrivacy?.ccpa?.usPrivacy == validCcpa as String

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
        def invalidCcpa = new BogusConsent()
        def ampRequest = getCcpaAmpRequest(invalidCcpa)

        and: "Save storedRequest into DB"
        def ampStoredRequest = bidRequestWithGeo
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
            privacy.originPrivacy?.ccpa?.usPrivacy == invalidCcpa as String
            privacy.resolvedPrivacy?.ccpa?.usPrivacy == invalidCcpa as String

            privacy.privacyActionsPerBidder[BidderName.GENERIC].isEmpty()

            privacy.errors ==
                    ["Amp request parameter consent_string or gdpr_consent have invalid format: $invalidCcpa" as String]
        }
    }
}
