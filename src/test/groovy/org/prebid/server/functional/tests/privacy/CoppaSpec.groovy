package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.response.auction.ErrorType
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.auction.TraceLevel.BASIC
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

    def "PBS auction should masking ip and ipv6 and emit warning when coppa = 1 and trace=#trace were passed"() {
        given: "Default bid request with regs.coppa = 1"
        def bidRequest = bidRequestWithGeo.tap {
            regs.coppa = 1
            ext.prebid.trace = trace
        }

        when: "PBS processes auction request"
        def bidResponse = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked ip and ipv6"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device.ip == "43.77.114.0"
        assert bidderRequests.device.ipv6 == "af47:892b:3e98:b400::"
        assert bidderRequests.device.ip != bidRequest.device.ip
        assert bidderRequests.device.ipv6 != bidRequest.device.ipv6

        and: "Should contain warning"
        assert bidResponse.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert bidResponse.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["IP address being rounded due to COPPA flag"]

        where:
        trace << [BASIC, VERBOSE]
    }

    def "PBS auction should skip masking ip and ipv6 and emit warning when coppa=#coppa and privacy.coppa.enabled=false and trace=#trace were passed"() {
        given: "PBS config with coppa enable"
        def prebidServerService = pbsServiceFactory.getService(["privacy.coppa.enabled": "false"])

        and: "Default bid request with regs.coppa=#coppa and trace"
        def bidRequest = bidRequestWithGeo.tap {
            regs.coppa = coppa
            ext.prebid.trace = trace
        }

        when: "PBS processes auction request"
        def bidResponse = prebidServerService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain same ip and ipv6 as requested"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device.ip == bidRequest.device.ip
        assert bidderRequests.device.ipv6 == bidRequest.device.ipv6

        and: "Should contain warning"
        assert bidResponse.ext?.warnings[ErrorType.PREBID]?.collect { it.code } == [999]
        assert bidResponse.ext?.warnings[ErrorType.PREBID]?.collect { it.message } ==
                ["IP address rounding skipped in COPPA environment due to the feature being disabled."]

        where:
        coppa | trace
        0     | VERBOSE
        1     | BASIC
        1     | VERBOSE
        0     | BASIC
    }
}
