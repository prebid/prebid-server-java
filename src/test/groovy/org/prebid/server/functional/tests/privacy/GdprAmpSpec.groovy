package org.prebid.server.functional.tests.privacy

import org.mockserver.model.Delay
import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.Regs
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.model.response.BidderErrorCode
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.BogusConsent
import org.prebid.server.functional.util.privacy.CcpaConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.VendorListConsent
import spock.lang.PendingFeature

import java.time.Instant

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.Purpose.P1
import static org.prebid.server.functional.model.config.Purpose.P2
import static org.prebid.server.functional.model.config.Purpose.P4
import static org.prebid.server.functional.model.config.PurposeEnforcement.BASIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.mock.services.vendorlist.GvlSpecificationVersion.V3
import static org.prebid.server.functional.model.pricefloors.Country.BULGARIA
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ADAPTER_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_REQUEST_DISALLOWED_COUNT
import static org.prebid.server.functional.model.request.amp.ConsentType.BOGUS
import static org.prebid.server.functional.model.request.amp.ConsentType.TCF_1
import static org.prebid.server.functional.model.request.amp.ConsentType.US_PRIVACY
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_EIDS
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_PRECISE_GEO
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.PublicCountryIp.BGR_IP
import static org.prebid.server.functional.model.response.auction.ErrorType.PREBID
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.DEVICE_ACCESS
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V4
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V5

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
        assert response.ext?.warnings[PREBID]*.code == [BidderErrorCode.GENERIC]
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
        assert response.ext?.errors[PREBID]*.code == [BidderErrorCode.GENERIC]
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
        assert response.ext?.errors[PREBID]*.code == [BidderErrorCode.GENERIC]
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
        assert response.ext?.errors[PREBID]*.code == [BidderErrorCode.GENERIC]
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
        assert response.ext?.warnings[PREBID]*.code == [BidderErrorCode.GENERIC]
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
                       new AccountGdprConfig(enabled: false, channelEnabledSnakeCase: [(ChannelType.AMP): true]),
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
        def config = GDPR_VENDOR_LIST_CONFIG + ["adapters.generic.meta-info.vendor-id": GENERIC_VENDOR_ID as String]
        def defaultPrivacyPbsService = pbsServiceFactory.getService(config)

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
        defaultPrivacyPbsService.sendAmpRequest(ampRequest)

        then: "Used vendor list have proper specification version of GVL"
        def properVendorListPath = VENDOR_LIST_PATH.replace("{VendorVersion}", tcfPolicyVersion.vendorListVersion.toString())
        PBSUtils.waitUntil { defaultPrivacyPbsService.isFileExist(properVendorListPath) }
        def vendorList = defaultPrivacyPbsService.getValueFromContainer(properVendorListPath, VendorListConsent.class)
        assert vendorList.tcfPolicyVersion == tcfPolicyVersion.vendorListVersion

        and: "Logs should contain proper vendor list version"
        def logs = defaultPrivacyPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Created new TCF 2 vendor list for version " +
                "v${tcfPolicyVersion.vendorListVersion}.${tcfPolicyVersion.vendorListVersion}")

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(config)

        where:
        tcfPolicyVersion << [TCF_POLICY_V2, TCF_POLICY_V4, TCF_POLICY_V5]
    }

    def "PBS amp shouldn't reject request with proper warning and metrics when incoming consent.tcfPolicyVersion have invalid parameter"() {
        given: "Tcf consent string with invalid tcf policy version"
        def tcfConsent = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setTcfPolicyVersion(invalidTcfPolicyVersion)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "AMP request"
        def ampRequest = getGdprAmpRequest(tcfConsent)

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Flush metrics"
        flushMetrics(privacyPbsService)

        when: "PBS processes amp request"
        def response = privacyPbsService.sendAmpRequest(ampRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[PREBID]*.code == [BidderErrorCode.GENERIC]
        assert response.ext?.warnings[PREBID]*.message ==
                ["Unknown tcfPolicyVersion ${invalidTcfPolicyVersion}, defaulting to gvlSpecificationVersion=3" as String]

        and: "Alerts.general metrics should be populated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert metrics[ALERT_GENERAL] == 1

        and: "Bidder should be called"
        assert bidder.getBidderRequest(ampStoredRequest.id)

        where:
        invalidTcfPolicyVersion << [MIN_INVALID_TCF_POLICY_VERSION,
                                    PBSUtils.getRandomNumber(MIN_INVALID_TCF_POLICY_VERSION, MAX_INVALID_TCF_POLICY_VERSION),
                                    MAX_INVALID_TCF_POLICY_VERSION]
    }

    def "PBS amp should emit the same error without a second GVL list request if a retry is too soon for the exponential-backoff"() {
        given: "Prebid server with privacy settings"
        def defaultPrivacyPbsService = pbsServiceFactory.getService(GENERAL_PRIVACY_CONFIG)

        and: "Test start time"
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
        defaultPrivacyPbsService.sendAmpRequest(ampRequest)

        then: "PBS shouldn't fetch vendor list"
        def vendorListPath = VENDOR_LIST_PATH.replace("{VendorVersion}", tcfPolicyVersion.vendorListVersion.toString())
        assert !defaultPrivacyPbsService.isFileExist(vendorListPath)

        and: "Logs should contain proper vendor list version"
        def logs = defaultPrivacyPbsService.getLogsByTime(startTime)
        def tcfError = "TCF 2 vendor list for version v${tcfPolicyVersion.vendorListVersion}.${tcfPolicyVersion.vendorListVersion} not found, started downloading."
        assert getLogsByText(logs, tcfError)

        and: "Second start for fetch second round of logs"
        def secondStartTime = Instant.now()

        when: "PBS processes amp request"
        defaultPrivacyPbsService.sendAmpRequest(ampRequest)

        then: "PBS shouldn't fetch vendor list"
        assert !defaultPrivacyPbsService.isFileExist(vendorListPath)

        and: "Logs should contain proper vendor list version"
        def logsSecond = defaultPrivacyPbsService.getLogsByTime(secondStartTime)
        assert getLogsByText(logsSecond, tcfError)

        and: "Reset vendor list response"
        vendorListResponse.reset()

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(GENERAL_PRIVACY_CONFIG)

        where:
        tcfPolicyVersion << [TCF_POLICY_V2, TCF_POLICY_V4, TCF_POLICY_V5]
    }

    def "PBS amp should update activity controls fetch bids metrics when tcf requirement disallow request"() {
        given: "Default ampStoredRequests with personal data"
        def ampStoredRequest = bidRequestWithPersonalData

        and: "Amp default request"
        def tcfConsent = new TcfConsent.Builder().build()
        def ampRequest = getGdprAmpRequest(tcfConsent).tap {
            account = ampStoredRequest.accountId
        }

        and: "Save account config with requireConsent into DB"
        def purposes = [(P2): new PurposeConfig(enforcePurpose: BASIC, enforceVendors: true)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(ampStoredRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Flush metric"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction requests"
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "PBS should cansel request"
        assert !bidder.getBidderRequests(ampStoredRequest.id)

        then: "Metrics processed across activities should be updated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
    }

    def "PBS auction should update activity controls privacy metrics when tcf requirement disallow privacy fields"() {
        given: "Default ampStoredRequests with personal data"
        def ampStoredRequest = bidRequestWithPersonalData

        and: "Amp default request"
        def tcfConsent = new TcfConsent.Builder().build()
        def ampRequest = getGdprAmpRequest(tcfConsent).tap {
            account = ampStoredRequest.accountId
        }

        and: "Save account config with requireConsent into DB"
        def purposes = [(P2): new PurposeConfig(enforcePurpose: NO, enforceVendors: false)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(ampStoredRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Flush metric"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction requests"
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll(bidderRequest) {
            bidderRequest.device.ip == "43.77.114.0"
            bidderRequest.device.ipv6 == "af47:892b:3e98:b400::"
            bidderRequest.device.geo.lat == ampStoredRequest.device.geo.lat.round(2)
            bidderRequest.device.geo.lon == ampStoredRequest.device.geo.lon.round(2)

            bidderRequest.device.geo.country == ampStoredRequest.device.geo.country
            bidderRequest.device.geo.region == ampStoredRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == ampStoredRequest.device.geo.utcoffset
        }

        and: "Bidder request should mask device personal data"
        verifyAll(bidderRequest.device) {
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
        verifyAll(bidderRequest.user) {
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

        and: "Metrics processed across activities should be updated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_PRECISE_GEO)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_PRECISE_GEO)] == 1
    }

    def "PBS auction should not update activity controls privacy metrics when tcf requirement allow privacy fields"() {
        given: "Default ampStoredRequests with personal data"
        def ampStoredRequest = bidRequestWithPersonalData

        and: "Amp default request"
        def tcfConsent = new TcfConsent.Builder().setSpecialFeatureOptIns(DEVICE_ACCESS).build()
        def ampRequest = getGdprAmpRequest(tcfConsent).tap {
            account = ampStoredRequest.accountId
        }

        and: "Save account config with requireConsent into DB"
        def purposes = [(P1): new PurposeConfig(enforcePurpose: NO, enforceVendors: false),
                        (P2): new PurposeConfig(enforcePurpose: NO, enforceVendors: false),
                        (P4): new PurposeConfig(enforcePurpose: NO, enforceVendors: false),
        ]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(ampStoredRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Flush metric"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction requests"
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request shouldn't mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(ampStoredRequest.id)
        verifyAll(bidderRequest) {
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

        and: "Metrics processed across activities shouldn't be updated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_PRECISE_GEO)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, TRANSMIT_PRECISE_GEO)]
    }

    def "PBS amp should set 3 for tcfPolicyVersion when tcfPolicyVersion is #tcfPolicyVersion"() {
        given: "Prebid server with privacy settings"
        def defaultPrivacyPbsService = pbsServiceFactory.getService(GENERAL_PRIVACY_CONFIG)

        and: "Tcf consent setup"
        def tcfConsent = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setTcfPolicyVersion(tcfPolicyVersion.value)
                .setVendorListVersion(tcfPolicyVersion.vendorListVersion)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "AMP request"
        def ampRequest = getGdprAmpRequest(tcfConsent)

        and: "Default stored request"
        def ampStoredRequest = BidRequest.defaultStoredRequest

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Set vendor list response"
        vendorListResponse.setResponse(tcfPolicyVersion)

        when: "PBS processes amp request"
        defaultPrivacyPbsService.sendAmpRequest(ampRequest)

        then: "Used vendor list have proper specification version of GVL"
        def properVendorListPath = VENDOR_LIST_PATH.replace("{VendorVersion}", tcfPolicyVersion.vendorListVersion.toString())
        PBSUtils.waitUntil { defaultPrivacyPbsService.isFileExist(properVendorListPath) }
        def vendorList = defaultPrivacyPbsService.getValueFromContainer(properVendorListPath, VendorListConsent.class)
        assert vendorList.gvlSpecificationVersion == V3

        cleanup: "Stop and remove pbs container"
        pbsServiceFactory.removeContainer(GENERAL_PRIVACY_CONFIG)

        where:
        tcfPolicyVersion << [TCF_POLICY_V4, TCF_POLICY_V5]
    }

    def "PBS should process with GDPR enforcement when GDPR and COPPA configurations are present in request"() {
        given: "Valid consent string without basic ads"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(DEVICE_ACCESS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Amp default request"
        def ampRequest = getGdprAmpRequest(validConsentString)

        and: "Bid request with gdpr and coppa config"
        def ampStoredRequest = getGdprBidRequest(DistributionChannel.SITE, validConsentString).tap {
            regs = new Regs(gdpr: gdpr, coppa: coppa, ext: new RegsExt(gdpr: extGdpr, coppa: extCoppa))
            setAccountId(ampRequest.account)
        }

        and: "Save account config without eea countries into DB"
        def accountGdprConfig = new AccountGdprConfig(enabled: true, eeaCountries: PBSUtils.getRandomEnum(Country.class, [BULGARIA]))
        def account = getAccountWithGdpr(ampRequest.account, accountGdprConfig)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Flush metrics"
        flushMetrics(privacyPbsService)

        when: "PBS processes amp request"
        privacyPbsService.sendAmpRequest(ampRequest)

        then: "Bidder shouldn't be called"
        assert !bidder.getBidderRequests(ampStoredRequest.id)

        then: "Metrics processed across activities should be updated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1

        where:
        gdpr | coppa | extGdpr | extCoppa
        1    | 1     | 1       | 1
        1    | 1     | 1       | 0
        1    | 1     | 1       | null
        1    | 1     | 0       | 1
        1    | 1     | 0       | 0
        1    | 1     | 0       | null
        1    | 1     | null    | 1
        1    | 1     | null    | 0
        1    | 1     | null    | null
        1    | 0     | 1       | 1
        1    | 0     | 1       | 0
        1    | 0     | 1       | null
        1    | 0     | 0       | 1
        1    | 0     | 0       | 0
        1    | 0     | 0       | null
        1    | 0     | null    | 1
        1    | 0     | null    | 0
        1    | 0     | null    | null
        1    | null  | 1       | 1
        1    | null  | 1       | 0
        1    | null  | 1       | null
        1    | null  | 0       | 1
        1    | null  | 0       | 0
        1    | null  | 0       | null
        1    | null  | null    | 1
        1    | null  | null    | 0
        1    | null  | null    | null

        null | 1     | 1       | 1
        null | 1     | 1       | 0
        null | 1     | 1       | null
        null | 0     | 1       | 1
        null | 0     | 1       | 0
        null | 0     | 1       | null
        null | null  | 1       | 1
        null | null  | 1       | 0
        null | null  | 1       | null
    }

    def "PBS should process with GDPR enforcement when request comes from EEA IP with COPPA enabled"() {
        given: "Valid consent string without basic ads"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(DEVICE_ACCESS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Amp default request"
        def ampRequest = getGdprAmpRequest(validConsentString)

        and: "Bid request with gdpr and coppa config"
        def ampStoredRequest = getGdprBidRequest(DistributionChannel.SITE, validConsentString).tap {
            regs = new Regs(gdpr: 1, coppa: 1, ext: new RegsExt(gdpr: 1, coppa: 1))
            device.geo.country = requestCountry
            device.geo.region = null
            device.ip = requestIpV4
            device.ipv6 = requestIpV6
        }

        and: "Save account config without eea countries into DB"
        def accountGdprConfig = new AccountGdprConfig(enabled: true, eeaCountries: accountCountry)
        def account = getAccountWithGdpr(ampRequest.account, accountGdprConfig)
        accountDao.save(account)

        and: "Stored request in DB"
        def storedRequest = StoredRequest.getStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Flush metrics"
        flushMetrics(privacyPbsService)

        when: "PBS processes amp request"
        privacyPbsService.sendAmpRequest(ampRequest, header)

        then: "Bidder shouldn't be called"
        assert !bidder.getBidderRequests(ampStoredRequest.id)

        then: "Metrics processed across activities should be updated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(ampStoredRequest, FETCH_BIDS)] == 1

        where:
        requestCountry | accountCountry | requestIpV4 | requestIpV6 | header
        BULGARIA       | BULGARIA       | BGR_IP.v4   | BGR_IP.v6   | ["X-Forwarded-For": BGR_IP.v4]
        BULGARIA       | null           | BGR_IP.v4   | BGR_IP.v6   | ["X-Forwarded-For": BGR_IP.v4]
        BULGARIA       | BULGARIA       | BGR_IP.v4   | null        | ["X-Forwarded-For": BGR_IP.v4]
        BULGARIA       | null           | BGR_IP.v4   | null        | ["X-Forwarded-For": BGR_IP.v4]
        BULGARIA       | BULGARIA       | null        | BGR_IP.v6   | ["X-Forwarded-For": BGR_IP.v4]
        BULGARIA       | null           | null        | BGR_IP.v6   | ["X-Forwarded-For": BGR_IP.v4]
        BULGARIA       | BULGARIA       | null        | null        | ["X-Forwarded-For": BGR_IP.v4]
        BULGARIA       | null           | null        | null        | ["X-Forwarded-For": BGR_IP.v4]
        null           | BULGARIA       | BGR_IP.v4   | BGR_IP.v6   | ["X-Forwarded-For": BGR_IP.v4]
        null           | null           | BGR_IP.v4   | BGR_IP.v6   | ["X-Forwarded-For": BGR_IP.v4]
        null           | BULGARIA       | BGR_IP.v4   | null        | ["X-Forwarded-For": BGR_IP.v4]
        null           | null           | BGR_IP.v4   | null        | ["X-Forwarded-For": BGR_IP.v4]
        null           | BULGARIA       | null        | BGR_IP.v6   | ["X-Forwarded-For": BGR_IP.v4]
        null           | null           | null        | BGR_IP.v6   | ["X-Forwarded-For": BGR_IP.v4]
        null           | BULGARIA       | null        | null        | ["X-Forwarded-For": BGR_IP.v4]
        null           | null           | null        | null        | ["X-Forwarded-For": BGR_IP.v4]
        BULGARIA       | BULGARIA       | BGR_IP.v4   | BGR_IP.v6   | [:]
        BULGARIA       | null           | BGR_IP.v4   | BGR_IP.v6   | [:]
        BULGARIA       | BULGARIA       | BGR_IP.v4   | null        | [:]
        BULGARIA       | null           | BGR_IP.v4   | null        | [:]
        BULGARIA       | BULGARIA       | null        | BGR_IP.v6   | [:]
        BULGARIA       | null           | null        | BGR_IP.v6   | [:]
        BULGARIA       | BULGARIA       | null        | null        | [:]
        BULGARIA       | null           | null        | null        | [:]
        null           | BULGARIA       | BGR_IP.v4   | BGR_IP.v6   | [:]
        null           | null           | BGR_IP.v4   | BGR_IP.v6   | [:]
        null           | BULGARIA       | BGR_IP.v4   | null        | [:]
        null           | null           | BGR_IP.v4   | null        | [:]
        null           | BULGARIA       | null        | BGR_IP.v6   | [:]
        null           | null           | null        | BGR_IP.v6   | [:]
        null           | BULGARIA       | null        | null        | [:]
        null           | null           | null        | null        | [:]
    }
}
