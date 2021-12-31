package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.privacy.BogusConsent
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.request.amp.ConsentType.BOGUS
import static org.prebid.server.functional.model.request.amp.ConsentType.TCF_1
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

@PBSTest
class GdprSpec extends PrivacyBaseSpec {

    @PendingFeature
    def "PBS should add debug log for auction request when valid gdpr was passed"() {
        given: "Default gdpr BidRequest"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .build()

        def bidRequest = getGdprBidRequest(validConsentString)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should contain debug log"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.tcf?.gdpr == "1"
            privacy.originPrivacy?.tcf?.tcfConsentString == validConsentString as String
            !privacy.originPrivacy?.tcf?.tcfConsentVersion
            !privacy.originPrivacy?.tcf?.inEea
            privacy.resolvedPrivacy?.tcf?.gdpr == "1"
            privacy.resolvedPrivacy?.tcf?.tcfConsentString == validConsentString as String
            privacy.resolvedPrivacy?.tcf?.tcfConsentVersion == 2
            !privacy.resolvedPrivacy?.tcf?.inEea

            !privacy.originPrivacy?.ccpa?.usPrivacy
            !privacy.resolvedPrivacy?.ccpa?.usPrivacy

            !privacy.originPrivacy?.coppa?.coppa
            privacy.resolvedPrivacy?.coppa?.coppa == 0

            privacy.privacyActionsPerBidder[BidderName.GENERIC] ==
                    ["Geolocation was masked in request to bidder according to TCF policy."]

            privacy.errors?.isEmpty()
        }
    }

    @PendingFeature
    def "PBS should add debug log for auction request when invalid gdpr was passed"() {
        given: "Default gdpr BidRequest"
        def invalidConsentString = new BogusConsent()
        def bidRequest = getGdprBidRequest(invalidConsentString)

        when: "PBS processes auction request"
        def response = defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Response should not contain ext.errors"
        assert !response.ext?.errors

        and: "Response should contain debug log with error"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.tcf?.gdpr == "1"
            privacy.originPrivacy?.tcf?.tcfConsentString == invalidConsentString as String
            !privacy.originPrivacy?.tcf?.tcfConsentVersion
            !privacy.originPrivacy?.tcf?.inEea
            privacy.resolvedPrivacy?.tcf?.gdpr == "1"
            privacy.resolvedPrivacy?.tcf?.tcfConsentString == invalidConsentString as String
            privacy.resolvedPrivacy?.tcf?.tcfConsentVersion == 2
            !privacy.resolvedPrivacy?.tcf?.inEea

            privacy.privacyActionsPerBidder[BidderName.GENERIC].isEmpty()

            privacy.errors == ["Placeholder: invalid consent string"]
        }
    }

    @PendingFeature
    def "PBS should add debug log for amp request when valid gdpr was passed"() {
        given: "AmpRequest with consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .build()

        def ampRequest = getGdprAmpRequest(validConsentString)

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
            privacy.originPrivacy?.tcf?.gdpr == "1"
            privacy.originPrivacy?.tcf?.tcfConsentString == validConsentString as String
            !privacy.originPrivacy?.tcf?.tcfConsentVersion
            !privacy.originPrivacy?.tcf?.inEea
            privacy.resolvedPrivacy?.tcf?.gdpr == "1"
            privacy.resolvedPrivacy?.tcf?.tcfConsentString == validConsentString as String
            privacy.resolvedPrivacy?.tcf?.tcfConsentVersion == 2
            !privacy.resolvedPrivacy?.tcf?.inEea

            !privacy.originPrivacy?.ccpa?.usPrivacy
            !privacy.resolvedPrivacy?.ccpa?.usPrivacy

            !privacy.originPrivacy?.coppa?.coppa
            privacy.resolvedPrivacy?.coppa?.coppa == 0

            privacy.privacyActionsPerBidder[BidderName.GENERIC] ==
                    ["Geolocation was masked in request to bidder according to TCF policy."]

            privacy.errors?.isEmpty()
        }
    }

    @PendingFeature
    def "PBS should add debug log for amp request when invalid gdpr was passed"() {
        given: "Default AmpRequest"
        def invalidConsentString = new BogusConsent()
        def ampRequest = getGdprAmpRequest(invalidConsentString)

        and: "Save storedRequest into DB"
        def ampStoredRequest = bidRequestWithGeo
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain debug log with error"
        assert response.ext?.debug?.privacy
        def privacy = response.ext?.debug?.privacy

        verifyAll {
            privacy.originPrivacy?.tcf?.gdpr == "1"
            privacy.originPrivacy?.tcf?.tcfConsentString == invalidConsentString as String
            !privacy.originPrivacy?.tcf?.tcfConsentVersion
            !privacy.originPrivacy?.tcf?.inEea
            privacy.resolvedPrivacy?.tcf?.gdpr == "1"
            privacy.resolvedPrivacy?.tcf?.tcfConsentString == invalidConsentString as String
            privacy.resolvedPrivacy?.tcf?.tcfConsentVersion == 2
            !privacy.resolvedPrivacy?.tcf?.inEea

            privacy.privacyActionsPerBidder[BidderName.GENERIC].isEmpty()

            privacy.errors == ["Amp request parameter consent_string or gdpr_consent have invalid format:" +
                                       " $invalidConsentString" as String]
        }
    }

    def "PBS should emit error for amp request when gdpr_consent is invalid"() {
        given: "Default AmpRequest with invalid gdpr_consent"
        def ampRequest = getGdprAmpRequest(invalidTcfConsent)
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.PREBID]*.code == [999]
        assert response.ext?.errors[ErrorType.PREBID]*.message ==
                ["Amp request parameter gdpr_consent has invalid format for consent type tcfV2: $invalidTcfConsent" as String]

        where:
        invalidTcfConsent << [new BogusConsent(), new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)]
    }

    def "PBS should emit error for amp request when consent_type is tcf1"() {
        given: "Default AmpRequest with consent_type = tcf1"
        def consentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .build()
        def ampRequest = getGdprAmpRequest(consentString).tap {
            consentType = TCF_1
        }
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.PREBID]*.code == [999]
        assert response.ext?.errors[ErrorType.PREBID]*.message == ["Consent type tcfV1 is no longer supported"]
    }

    def "PBS should emit error for amp request with consentString when consent_type is bogus"() {
        given: "Default AmpRequest with invalid consent_type"
        def consentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .build()
        def ampRequest = getGdprAmpRequest(consentString).tap {
            consentType = BOGUS
        }
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.PREBID]*.code == [999]
        assert response.ext?.errors[ErrorType.PREBID]*.message == ["Invalid consent_type param passed"]
    }

    def "PBS should emit error for amp request when set not appropriate ccpa consent"() {
        given: "Default AmpRequest"
        def ccpaConsent = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def ampRequest = getGdprAmpRequest(null).tap {
            consentString = ccpaConsent
        }
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.errors[ErrorType.PREBID]*.code == [999]
        assert response.ext?.errors[ErrorType.PREBID]*.message ==
                ["Amp request parameter consent_string has invalid format for consent type tcfV2: $ccpaConsent" as String]
    }
}
