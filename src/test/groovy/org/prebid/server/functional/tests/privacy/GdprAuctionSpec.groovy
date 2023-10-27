package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.request.auction.DistributionChannel
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
import static org.prebid.server.functional.model.request.auction.Prebid.Channel
import static org.prebid.server.functional.model.response.auction.BidRejectionReason.REJECTED_BY_PRIVACY
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.BASIC_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V3

class GdprAuctionSpec extends PrivacyBaseSpec {

    @PendingFeature
    def "PBS should add debug log for auction request when valid gdpr was passed"() {
        given: "Default gdpr BidRequest"
        def validConsentString = new TcfConsent.Builder()
                .setPurposesLITransparency(BASIC_ADS)
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
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
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
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
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
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
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
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
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
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
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
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
                .addVendorLegitimateInterest([GENERIC_VENDOR_ID])
                .build()

        and: "Bid request"
        def bidRequest = getGdprBidRequest(tcfConsent)

        and: "Set vendor list response"
        vendorListResponse.setResponse(tcfPolicyVersion)

        when: "PBS processes auction request"
        privacyPbsService.sendAuctionRequest(bidRequest)

        then: "Used vendor list have proper specification version of GVL"
        def properVendorListPath = "/app/prebid-server/data/vendorlist-v${tcfPolicyVersion.vendorListVersion}/${tcfPolicyVersion.vendorListVersion}.json"
        PBSUtils.waitUntil { privacyPbsService.isFileExist(properVendorListPath) }
        def vendorList = privacyPbsService.getValueFromContainer(properVendorListPath, VendorListConsent.class)
        assert vendorList.vendorListVersion == tcfPolicyVersion.vendorListVersion

        and: "Logs should contain proper vendor list version"
        def logs = privacyPbsService.getLogsByTime(startTime)
        assert getLogsByText(logs, "Created new TCF 2 vendor list for version ${tcfPolicyVersion.vendorListVersion}")

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
}
