package org.prebid.server.functional.tests.privacy

import org.mockserver.model.Delay
import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.config.PurposeEnforcement
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.model.response.auction.ErrorType
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.BogusConsent
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.VendorListConsent
import spock.lang.PendingFeature

import java.time.Instant

import static org.prebid.server.functional.model.ChannelType.PBJS
import static org.prebid.server.functional.model.ChannelType.WEB
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.pricefloors.Country.BULGARIA
import static org.prebid.server.functional.model.pricefloors.Country.CAN
import static org.prebid.server.functional.model.pricefloors.Country.USA
import static org.prebid.server.functional.model.config.Purpose.P1
import static org.prebid.server.functional.model.config.Purpose.P2
import static org.prebid.server.functional.model.config.Purpose.P4
import static org.prebid.server.functional.model.config.PurposeEnforcement.*
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ACCOUNT_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_ADAPTER_DISALLOWED_COUNT
import static org.prebid.server.functional.model.privacy.Metric.TEMPLATE_REQUEST_DISALLOWED_COUNT
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_EIDS
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_PRECISE_GEO
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.Prebid.Channel
import static org.prebid.server.functional.model.request.auction.TraceLevel.BASIC
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REJECTED_BY_PRIVACY
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.DEVICE_ACCESS
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V3

class GdprAuctionSpec extends PrivacyBaseSpec {

    @PendingFeature
    def "PBS should add debug log for auction request when valid gdpr was passed"() {
        given: "Default gdpr BidRequest"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        def bidRequest = getGdprBidRequest(validConsentString)

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

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
    def "PBS should add debug log for auction request when invalid gdpr was passed"() {
        given: "Default gdpr BidRequest"
        def invalidConsentString = new BogusConsent()
        def bidRequest = getGdprBidRequest(invalidConsentString)

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

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

            privacy.privacyActionsPerBidder[GENERIC].isEmpty()

            privacy.errors == ["Placeholder: invalid consent string"]
        }
    }

    def "PBS should apply gdpr when privacy.gdpr.channel-enabled.app or privacy.gdpr.enabled = true in account config"() {
        given: "Default basic generic BidRequest"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()
        def bidRequest = getGdprBidRequest(DistributionChannel.APP, validConsentString)

        and: "Save account config into DB"
        accountDao.save(getAccountWithGdpr(bidRequest.app.publisher.id, gdprConfig))

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo == maskGeo(bidRequest)

        where:
        gdprConfig << [new AccountGdprConfig(enabled: false, channelEnabled: [(ChannelType.APP): true]),
                       new AccountGdprConfig(enabled: true)]
    }

    def "PBS should apply gdpr when privacy.gdpr.channel-enabled.web or privacy.gdpr.enabled = true in account config"() {
        given: "Default basic generic BidRequest"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()
        def bidRequest = getGdprBidRequest(validConsentString)

        and: "Save account config into DB"
        accountDao.save(getAccountWithGdpr(bidRequest.site.publisher.id, gdprConfig))

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo == maskGeo(bidRequest)

        where:
        gdprConfig << [new AccountGdprConfig(enabled: true),
                       new AccountGdprConfig(enabled: false, channelEnabled: [(WEB): true])]
    }

    def "PBS should not apply gdpr when privacy.gdpr.channel-enabled.app or privacy.gdpr.enabled = false in account config"() {
        given: "Default basic generic BidRequest"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()
        def bidRequest = getGdprBidRequest(DistributionChannel.APP, validConsentString)

        and: "Save account config into DB"
        accountDao.save(getAccountWithGdpr(bidRequest.app.publisher.id, gdprConfig))

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == bidRequest.device.geo.lat
        assert bidderRequests.device?.geo?.lon == bidRequest.device.geo.lon

        where:
        gdprConfig << [new AccountGdprConfig(enabled: true, channelEnabled: [(ChannelType.APP): false]),
                       new AccountGdprConfig(enabled: false)]
    }

    def "PBS should not apply gdpr when privacy.gdpr.channel-enabled.web or privacy.gdpr.enabled = false in account config"() {
        given: "Default basic generic BidRequest"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()
        def bidRequest = getGdprBidRequest(validConsentString)

        and: "Save account config into DB"
        accountDao.save(getAccountWithGdpr(bidRequest.site.publisher.id, gdprConfig))

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == bidRequest.device.geo.lat
        assert bidderRequests.device?.geo?.lon == bidRequest.device.geo.lon

        where:
        gdprConfig << [new AccountGdprConfig(enabled: true, channelEnabled: [(WEB): false]),
                       new AccountGdprConfig(enabled: false)]
    }

    def "PBS should recognise 'web' and 'pbjs' as the same channel when privacy.gdpr config is defined in account"() {
        given: "BidRequest with channel: #requestChannel, gdpr"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()
        def bidRequest = getGdprBidRequest(validConsentString).tap {
            ext.prebid.channel = new Channel().tap {
                name = requestChannel
            }
        }

        and: "Save account config #accountChannel = true with into DB"
        def gdprConfig = new AccountGdprConfig(enabled: false, channelEnabled: [(accountChannel): true])
        def account = getAccountWithGdpr(bidRequest.site.publisher.id, gdprConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo == maskGeo(bidRequest)

        where:
        requestChannel | accountChannel
        WEB            | WEB
        WEB            | PBJS
        PBJS           | WEB
        PBJS           | PBJS
    }

    def "PBS should populate seatNonBid when bidder is rejected by privacy"() {
        given: "Default basic BidRequest with banner"
        def validConsentString = new TcfConsent.Builder().build()
        def bidRequest = getGdprBidRequest(validConsentString).tap {
            ext.prebid.returnAllBidStatus = true
        }

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBS response should contains seatNonBid"
        def seatNonBids = response.ext.seatnonbid
        assert seatNonBids.size() == 1

        def seatNonBid = seatNonBids[0]
        assert seatNonBid.seat == GENERIC.value
        assert seatNonBid.nonBid[0].impId == bidRequest.imp[0].id
        assert seatNonBid.nonBid[0].statusCode == REJECTED_BY_PRIVACY

        and: "seatbid should be empty"
        assert response.seatbid.isEmpty()
    }

    def "PBS auction should process request and cache correct vendorList file with proper consent.tcfPolicyVersion parameter"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Create new container"
        def serverContainer = new PrebidServerContainer(GDPR_VENDOR_LIST_CONFIG +
                ["adapters.generic.meta-info.vendor-id": GENERIC_VENDOR_ID as String])
        serverContainer.start()
        def privacyPbsService = new PrebidServerService(serverContainer)

        and: "Tcf consent setup"
        def tcfConsent = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setTcfPolicyVersion(tcfPolicyVersion.value)
                .setVendorListVersion(tcfPolicyVersion.vendorListVersion)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Bid request"
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Set vendor list response"
        vendorListResponse.setResponse(tcfPolicyVersion)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

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

    def "PBS auction should reject request with proper warning when incoming consent.tcfPolicyVersion have invalid parameter"() {
        given: "Tcf consent string"
        def invalidTcfPolicyVersion = PBSUtils.getRandomNumber(5, 63)
        def tcfConsent = new TcfConsent.Builder()
                .setTcfPolicyVersion(invalidTcfPolicyVersion)
                .build()

        and: "Bid request"
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Flush metrics"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction request"
        def response = privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bid response should contain warning"
        assert response.ext?.warnings[ErrorType.PREBID]*.code == [999]
        assert response.ext?.warnings[ErrorType.PREBID]*.message ==
                ["Parsing consent string: ${tcfConsent} failed. TCF policy version ${invalidTcfPolicyVersion} is not supported" as String]
    }

    def "PBS auction should emit the same error without a second GVL list request if a retry is too soon for the exponential-backoff"() {
        given: "Test start time"
        def startTime = Instant.now()

        and: "Tcf consent setup"
        def tcfConsent = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setTcfPolicyVersion(tcfPolicyVersion.value)
                .setVendorListVersion(tcfPolicyVersion.vendorListVersion)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Bid request"
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Reset valid vendor list response"
        vendorListResponse.reset()

        and: "Set vendor list response with delay"
        vendorListResponse.setResponse(tcfPolicyVersion, Delay.seconds(EXPONENTIAL_BACKOFF_MAX_DELAY + 3))

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Used vendor list have proper specification version of GVL"
        def properVendorListPath = VENDOR_LIST_PATH.replace("{VendorVersion}", tcfPolicyVersion.vendorListVersion.toString())
        assert !privacyPbsService.isFileExist(properVendorListPath)

        and: "Logs should contain proper vendor list version"
        def logs = privacyPbsService.getLogsByTime(startTime)
        def tcfError = "TCF 2 vendor list for version v${tcfPolicyVersion.vendorListVersion}.${tcfPolicyVersion.vendorListVersion} not found, started downloading."
        assert getLogsByText(logs, tcfError)

        and: "Second start for fetch second round of logs"
        def secondStartTime = Instant.now()

        when: "PBS processes amp request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBS shouldn't fetch vendor list"
        assert !privacyPbsService.isFileExist(properVendorListPath)

        and: "Logs should contain proper vendor list version"
        def logsSecond = privacyPbsService.getLogsByTime(secondStartTime)
        assert getLogsByText(logsSecond, tcfError)

        and: "Reset vendor list response"
        vendorListResponse.reset()

        where:
        tcfPolicyVersion << [TCF_POLICY_V2, TCF_POLICY_V3]
    }

    def "PBS should apply gdpr and emit metrics when host and device.geo.country contains same eea-country"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Gpdr bid request with override country"
        def bidRequest = getGdprBidRequest(DistributionChannel.APP, validConsentString).tap {
            device.geo.country = BULGARIA
        }

        and: "Save account config into DB"
        accountDao.save(getAccountWithGdpr(bidRequest.app.publisher.id,
                new AccountGdprConfig(enabled: true, eeaCountries: null)))

        and: "Flush metrics"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should increment metrics when eea-country matched"
        def metricsRequest = privacyPbsService.sendCollectedMetricsRequest()
        assert metricsRequest["privacy.tcf.v2.in-geo"] == 1
        assert !metricsRequest["privacy.tcf.v2.out-geo"]
    }

    def "PBS should apply gdpr and not emit metrics when host and device.geo.country doesn't contain same eea-country"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Gpdr bid request with override country"
        def bidRequest = getGdprBidRequest(DistributionChannel.APP, validConsentString).tap {
            device.geo.country = USA
        }

        and: "Save account config into DB"
        accountDao.save(getAccountWithGdpr(bidRequest.app.publisher.id,
                new AccountGdprConfig(enabled: true, eeaCountries: null)))

        and: "Flush metrics"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should increment metrics when eea-country doens't matched"
        def metricsRequest = privacyPbsService.sendCollectedMetricsRequest()
        assert !metricsRequest["privacy.tcf.v2.in-geo"]
        assert metricsRequest["privacy.tcf.v2.out-geo"] == 1
    }

    def "PBS should apply gdpr and emit metrics when account and device.geo.country contains same eea-country"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Gpdr bid request with override country"
        def bidRequest = getGdprBidRequest(DistributionChannel.APP, validConsentString).tap {
            device.geo.country = USA
        }

        and: "Save account config into DB"
        accountDao.save(getAccountWithGdpr(bidRequest.app.publisher.id,
                new AccountGdprConfig(enabled: true, eeaCountries: USA.ISOAlpha2)))

        and: "Flush metrics"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBs should increment metrics when eea-country matched"
        def metricsRequest = privacyPbsService.sendCollectedMetricsRequest()
        assert metricsRequest["privacy.tcf.v2.in-geo"] == 1
        assert !metricsRequest["privacy.tcf.v2.out-geo"]
    }

    def "PBS should apply gdpr and not emit metrics when account and device.geo.country doesn't contain same eea-country"() {
        given: "Valid consent string"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .setVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Gpdr bid request with override country"
        def bidRequest = getGdprBidRequest(DistributionChannel.APP, validConsentString).tap {
            device.geo.country = USA
        }

        and: "Save account config into DB"
        accountDao.save(getAccountWithGdpr(bidRequest.app.publisher.id,
                new AccountGdprConfig(enabled: true, eeaCountries: CAN.ISOAlpha2)))

        and: "Flush metrics"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBs shouldn't increment metrics when eea-country matched"
        def metricsRequest = privacyPbsService.sendCollectedMetricsRequest()
        assert !metricsRequest["privacy.tcf.v2.in-geo"]
        assert metricsRequest["privacy.tcf.v2.out-geo"] == 1
    }

    def "PBS auction should update activity controls fetch bids metrics when tcf requirement disallow request"() {
        given: "Default Generic bid requests with personal data"
        def tcfConsent = new TcfConsent.Builder().build()
        def bidRequest = bidRequestWithPersonalData.tap {
            regs.ext = new RegsExt(gdpr: 1)
            user.ext.consent = tcfConsent
        }

        and: "Save account config with requireConsent into DB"
        def purposes = [(P2): new PurposeConfig(enforcePurpose: PurposeEnforcement.BASIC, enforceVendors: true)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction requests"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "PBS should cansel request"
        assert !bidder.getBidderRequests(bidRequest.id)

        then: "Metrics processed across activities should be updated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, FETCH_BIDS)] == 1
    }

    def "PBS auction should update activity controls privacy metrics when tcf requirement disallow privacy fields and trace level verbosity"() {
        given: "Default Generic BidRequests with personal data"
        def tcfConsent = new TcfConsent.Builder().build()
        def bidRequest = bidRequestWithPersonalData.tap {
            regs.ext = new RegsExt(gdpr: 1)
            user.ext.consent = tcfConsent
            ext.prebid.trace = VERBOSE
        }

        and: "Save account config with requireConsent into DB"
        def purposes = [(P2): new PurposeConfig(enforcePurpose: NO, enforceVendors: false)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction requests"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            bidderRequest.device.ip == "43.77.114.0"
            bidderRequest.device.ipv6 == "af47:892b:3e98:b400::"
            bidderRequest.device.geo.lat == bidRequest.device.geo.lat.round(2)
            bidderRequest.device.geo.lon == bidRequest.device.geo.lon.round(2)

            bidderRequest.device.geo.country == bidRequest.device.geo.country
            bidderRequest.device.geo.region == bidRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == bidRequest.device.geo.utcoffset
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
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)] == 1
    }

    def "PBS auction should update activity controls privacy metrics when tcf requirement disallow privacy fields and trace level basic"() {
        given: "Default Generic BidRequests with personal data"
        def tcfConsent = new TcfConsent.Builder().build()
        def bidRequest = bidRequestWithPersonalData.tap {
            regs.ext = new RegsExt(gdpr: 1)
            user.ext.consent = tcfConsent
            ext.prebid.trace = BASIC
        }

        and: "Save account config with requireConsent into DB"
        def purposes = [(P2): new PurposeConfig(enforcePurpose: NO, enforceVendors: false)]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction requests"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
            bidderRequest.device.ip == "43.77.114.0"
            bidderRequest.device.ipv6 == "af47:892b:3e98:b400::"
            bidderRequest.device.geo.lat == bidRequest.device.geo.lat.round(2)
            bidderRequest.device.geo.lon == bidRequest.device.geo.lon.round(2)

            bidderRequest.device.geo.country == bidRequest.device.geo.country
            bidderRequest.device.geo.region == bidRequest.device.geo.region
            bidderRequest.device.geo.utcoffset == bidRequest.device.geo.utcoffset
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
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)] == 1
        assert metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)] == 1

        and: "Account metrics shouldn't be updated"
        assert !metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)]
    }

    def "PBS auction should not update activity controls privacy metrics when tcf requirement allow privacy fields"() {
        given: "Default Generic BidRequests with privacy data"
        def tcfConsent = new TcfConsent.Builder().setSpecialFeatureOptIns(DEVICE_ACCESS).build()
        def bidRequest = bidRequestWithPersonalData.tap {
            regs.ext = new RegsExt(gdpr: 1)
            user.ext.consent = tcfConsent
        }

        new TcfConsent.Builder().setPurposesConsent([]).build().consentString

        and: "Save account config with requireConsent into DB"
        def purposes = [(P1): new PurposeConfig(enforcePurpose: NO, enforceVendors: false),
                        (P2): new PurposeConfig(enforcePurpose: NO, enforceVendors: false),
                        (P4): new PurposeConfig(enforcePurpose: NO, enforceVendors: false),
        ]
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        and: "Flush metric"
        flushMetrics(privacyPbsService)

        when: "PBS processes auction requests"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request shouldn't mask device and user personal data"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        verifyAll(bidderRequest) {
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

        and: "Metrics processed across activities shouldn't be updated"
        def metrics = privacyPbsService.sendCollectedMetricsRequest()
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_ADAPTER_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)]
        assert !metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_ACCOUNT_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_UFPD)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_EIDS)]
        assert !metrics[TEMPLATE_REQUEST_DISALLOWED_COUNT.getValue(bidRequest, TRANSMIT_PRECISE_GEO)]
    }
}
