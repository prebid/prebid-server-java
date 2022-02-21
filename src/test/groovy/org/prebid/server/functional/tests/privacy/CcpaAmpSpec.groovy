package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.privacy.BogusConsent
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import spock.lang.PendingFeature
import spock.lang.Unroll

import static org.prebid.server.functional.model.ChannelType.AMP
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.amp.ConsentType.BOGUS
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS

@PBSTest
class CcpaAmpSpec extends PrivacyBaseSpec {

    @PendingFeature
    def "PBS should add debug log for amp request when valid ccpa was passed"() {
        given: "Default AmpRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def ampRequest = getCcpaAmpRequest(validCcpa)

        and: "Save storedRequest into DB"
        def ampStoredRequest = storedRequestWithGeo
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

            privacy.privacyActionsPerBidder[GENERIC] ==
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
        def ampStoredRequest = storedRequestWithGeo
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

            privacy.privacyActionsPerBidder[GENERIC].isEmpty()

            privacy.errors ==
                    ["Amp request parameter consent_string or gdpr_consent have invalid format: $invalidCcpa" as String]
        }
    }

    @Unroll
    def "PBS should apply ccpa when privacy.ccpa.channel-enabled.amp or privacy.ccpa.enabled = true in account config"() {
        given: "Default AmpRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def ampRequest = getCcpaAmpRequest(validCcpa)

        and: "Save storedRequest into DB"
        def ampStoredRequest = storedRequestWithGeo
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Save account config into DB"
        def privacy = new AccountPrivacyConfig(ccpa: ccpaConfig)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: ampRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequests.device?.geo == maskGeo(ampStoredRequest)

        where:
        ccpaConfig << [new AccountCcpaConfig(enabled: false, channelEnabled: [(AMP): true]),
                       new AccountCcpaConfig(enabled: true)]
    }

    @Unroll
    def "PBS should not apply ccpa when privacy.ccpa.channel-enabled.amp or privacy.ccpa.enabled = false in account config"() {
        given: "Default AmpRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def ampRequest = getCcpaAmpRequest(validCcpa)

        and: "Save storedRequest into DB"
        def ampStoredRequest = storedRequestWithGeo
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Save account config into DB"
        def privacy = new AccountPrivacyConfig(ccpa: ccpaConfig)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: ampRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequests.device?.geo?.lat == ampStoredRequest.device.geo.lat
        assert bidderRequests.device?.geo?.lon == ampStoredRequest.device.geo.lon

        where:
        ccpaConfig << [new AccountCcpaConfig(enabled: true, channelEnabled: [(AMP): false]),
                       new AccountCcpaConfig(enabled: false)]
    }

    def "PBS should emit error for amp request when consent_string contains invalid ccpa consent"() {
        given: "Default AmpRequest with invalid ccpa consent"
        def ampRequest = getCcpaAmpRequest(invalidCcpa)
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
                ["Amp request parameter consent_string has invalid format for consent " +
                         "type usPrivacy: $invalidCcpa" as String]

        where:
        invalidCcpa << [new BogusConsent(), new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .build()]
    }

    def "PBS should emit error for amp request with gdprConsent when consent_type is invalid"() {
        given: "Default AmpRequest with invalid ccpa type"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def ampRequest = getCcpaAmpRequest(validCcpa).tap {
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

    def "PBS should emit error for amp request when set not appropriate tcf consent"() {
        given: "Default AmpRequest with gdpr_consent"
        def tcfConsent = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .build()
        def ampRequest = getCcpaAmpRequest(null).tap {
            gdprConsent = tcfConsent
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
                ["Amp request parameter gdpr_consent has invalid format for consent type usPrivacy: $tcfConsent" as String]
    }
}
