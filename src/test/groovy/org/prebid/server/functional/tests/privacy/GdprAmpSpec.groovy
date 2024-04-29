package org.prebid.server.functional.tests.privacy

import org.mockserver.model.Delay
import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.BogusConsent
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.VendorListConsent
import spock.lang.PendingFeature

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.amp.ConsentType.BOGUS
import static org.prebid.server.functional.model.request.amp.ConsentType.TCF_1
import static org.prebid.server.functional.model.request.amp.ConsentType.US_PRIVACY
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V3

class GdprAmpSpec extends PrivacyBaseSpec {

    @PendingFeature
    def "PBS should add debug log for amp request when valid gdpr was passed"() {
        given: "AmpRequest with consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        def ampRequest = getGdprAmpRequest(validConsentString)

        and: "Save storedRequest into DB"
        def ampStoredRequest = storedRequestWithGeo
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = privacyPbsService.sendAmpRequest(ampRequest)

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

            privacy.privacyActionsPerBidder[GENERIC] ==
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
        def ampStoredRequest = storedRequestWithGeo
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = privacyPbsService.sendAmpRequest(ampRequest)

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

            privacy.privacyActionsPerBidder[GENERIC].isEmpty()

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
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = privacyPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message[0].startsWith("Parsing consent string:\"${invalidTcfConsent}\"")

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
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = privacyPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message == ["Consent type tcfV1 is no longer supported"]
    }

    def "PBS should emit error for amp request with consentString when consent_type is us_privacy"() {
        given: "Default AmpRequest with invalid consent_type"
        def consentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .build()
        def ampRequest = getGdprAmpRequest(consentString).tap {
            consentType = US_PRIVACY
        }
        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = defaultPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message ==
                ["CCPA consent $consentString has invalid format: " +
                         "us_privacy must contain 4 characters"]
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
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = privacyPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.errors[PREBID]*.code == [999]
        assert response.ext?.errors[PREBID]*.message == ["Invalid consent_type param passed"]
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
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = privacyPbsService.sendAmpRequest(ampRequest)

        then: "Response should contain error"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message[0] ==~ /Parsing consent string:"$ccpaConsent" - failed.*/
    }

    def "PBS should apply gdpr when privacy.gdpr.channel-enabled.amp or privacy.gdpr.enabled = true in account config"() {
        given: "Default AmpRequest"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()
        def ampRequest = getGdprAmpRequest(validConsentString)

        and: "Save storedRequest into DB"
        def ampStoredRequest = storedRequestWithGeo
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Save account config into DB"
        def privacy = new AccountPrivacyConfig(gdpr: gdprConfig)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: ampRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes amp request"
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequests.device?.geo == maskGeo(ampStoredRequest)

        where:
        gdprConfig << [new AccountGdprConfig(enabled: false, channelEnabled: [(ChannelType.AMP): true]),
                       new AccountGdprConfig(enabled: true)]
    }

    def "PBS should not apply gdpr when privacy.gdpr.channel-enabled.amp or privacy.gdpr.enabled = false in account config"() {
        given: "Default AmpRequest"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()
        def ampRequest = getGdprAmpRequest(validConsentString)

        and: "Save storedRequest into DB"
        def ampStoredRequest = storedRequestWithGeo
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Save account config into DB"
        def privacy = new AccountPrivacyConfig(gdpr: gdprConfig)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: ampRequest.account, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes amp request"
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequests.device?.geo?.lat == ampStoredRequest.device.geo.lat
        assert bidderRequests.device?.geo?.lon == ampStoredRequest.device.geo.lon

        where:
        gdprConfig << [new AccountGdprConfig(enabled: true, channelEnabled: [(ChannelType.AMP): false]),
                       new AccountGdprConfig(enabled: false)]
    }

    def "PBS amp with proper consent.tcfPolicyVersion parameter should process request and cache correct vendorList file"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Create new container"
        def serverContainer = new PrebidServerContainer(GDPR_VENDOR_LIST_CONFIG +
                ["adapters.generic.meta-info.vendor-id": GENERIC_VENDOR_ID as String])
        serverContainer.start()
        def privacyPbsService = new PrebidServerService(serverContainer)

        and: "Prepare tcf consent string"
        def tcfConsent = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setTcfPolicyVersion(tcfPolicyVersion.value)
                .setVendorListVersion(tcfPolicyVersion.vendorListVersion)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "AMP request"
        def ampRequest = getGdprAmpRequest(tcfConsent)

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Set vendor list response"
        vendorListResponse.setResponse(tcfPolicyVersion)

        when: "PBS processes amp request"
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "Used vendor list have proper specification version of GVL"
        def properVendorListPath = VENDOR_LIST_PATH.replace("{VendorVersion}", tcfPolicyVersion.vendorListVersion.toString())
        PBSUtils.waitUntil { privacyPbsService.isFileExist(properVendorListPath) }
        def vendorList = privacyPbsService.getValueFromContainer(properVendorListPath, VendorListConsent.class)
        assert vendorList.tcfPolicyVersion == tcfPolicyVersion.vendorListVersion

        and: "Logs should contain proper vendor list version"
        def logs = privacyPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Created new TCF 2 vendor list for version " +
                "v${tcfPolicyVersion.vendorListVersion}.${tcfPolicyVersion.vendorListVersion}")

        cleanup: "Stop container with default request"
        serverContainer.stop()

        where:
        tcfPolicyVersion << [TCF_POLICY_V2, TCF_POLICY_V3]
    }

    def "PBS amp with invalid consent.tcfPolicyVersion parameter should reject request and include proper warning"() {
        given: "Tcf consent string"
        def invalidTcfPolicyVersion = PBSUtils.getRandomNumber(5, 63)
        def tcfConsent = new TcfConsent.Builder()
                .setTcfPolicyVersion(invalidTcfPolicyVersion)
                .build()

        and: "AMP request"
        def ampRequest = getGdprAmpRequest(tcfConsent)

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        when: "PBS processes amp request"
        def response = privacyPbsService.sendAmpRequest(ampRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[PREBID]*.code == [999]
        assert response.ext?.warnings[PREBID]*.message ==
                ["Parsing consent string: ${tcfConsent} failed. TCF policy version ${invalidTcfPolicyVersion} is not supported" as String]
    }

    def "PBS amp should emit the same error without a second GVL list request if a retry is too soon for the exponential-backoff"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Prepare tcf consent string"
        def tcfConsent = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setTcfPolicyVersion(tcfPolicyVersion.value)
                .setVendorListVersion(tcfPolicyVersion.vendorListVersion)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "AMP request"
        def ampRequest = getGdprAmpRequest(tcfConsent)

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultBidRequest

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Reset valid vendor list response"
        vendorListResponse.reset()

        and: "Set vendor list response with delay"
        vendorListResponse.setResponse(tcfPolicyVersion, Delay.seconds(EXPONENTIAL_BACKOFF_MAX_DELAY + 3))

        when: "PBS processes amp request"
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "PBS shouldn't fetch vendor list"
        def vendorListPath = VENDOR_LIST_PATH.replace("{VendorVersion}", tcfPolicyVersion.vendorListVersion.toString())
        assert !privacyPbsService.isFileExist(vendorListPath)

        and: "Logs should contain proper vendor list version"
        def logs = privacyPbsService.getLogsByTime(startTime)
        def tcfError = "TCF 2 vendor list for version v${tcfPolicyVersion.vendorListVersion}.${tcfPolicyVersion.vendorListVersion} not found, started downloading."
        assert getLogsByText(logs, tcfError)

        and: "Second start for fetch second round of logs"
        def secondStartTime = Instant.now()

        when: "PBS processes amp request"
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "PBS shouldn't fetch vendor list"
        assert !privacyPbsService.isFileExist(vendorListPath)

        and: "Logs should contain proper vendor list version"
        def logsSecond = privacyPbsService.getLogsByTime(secondStartTime)
        assert getLogsByText(logsSecond, tcfError)

        and: "Reset vendor list response"
        vendorListResponse.reset()

        where:
        tcfPolicyVersion << [TCF_POLICY_V2, TCF_POLICY_V3]
    }
}
