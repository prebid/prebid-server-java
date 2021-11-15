package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.testcontainers.PBSTest
import spock.lang.PendingFeature

@PBSTest
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

            privacy.privacyActionsPerBidder[BidderName.GENERIC].isEmpty()

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

            privacy.privacyActionsPerBidder[BidderName.GENERIC] ==
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
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
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

            privacy.privacyActionsPerBidder[BidderName.GENERIC].isEmpty()

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
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.coppa?.coppa == ampStoredRequest.regs.coppa
            privacy.resolvedPrivacy?.coppa?.coppa == ampStoredRequest.regs.coppa

            privacy.privacyActionsPerBidder[BidderName.GENERIC] ==
                    ["Geolocation and address were removed from request to bidder according to CCPA policy."]

            privacy.errors?.isEmpty()
        }
    }
}
