package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.ChannelType
import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.request.auction.Channel
import org.prebid.server.functional.model.request.auction.DistributionChannel
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.privacy.BogusConsent
import org.prebid.server.functional.util.privacy.CcpaConsent
import spock.lang.PendingFeature

import static org.prebid.server.functional.model.ChannelType.PBJS
import static org.prebid.server.functional.model.ChannelType.WEB
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.util.privacy.CcpaConsent.Signal.ENFORCED

@PBSTest
class CcpaAuctionSpec extends PrivacyBaseSpec {

    // TODO: extend ccpa test with actual fields that we should mask
    def "PBS should mask publisher info when privacy.ccpa.enabled = true in account config"() {
        given: "Default ccpa BidRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def bidRequest = getCcpaBidRequest(validCcpa)

        and: "Save account config into DB"
        def ccpaConfig = new AccountCcpaConfig(enabled: true)
        accountDao.save(getAccountWithCcpa(bidRequest.site.publisher.id, ccpaConfig))

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
        def ccpaConfig = new AccountCcpaConfig(enabled: false)
        accountDao.save(getAccountWithCcpa(bidRequest.site.publisher.id, ccpaConfig))

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

            privacy.privacyActionsPerBidder[GENERIC] ==
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

            privacy.privacyActionsPerBidder[GENERIC].isEmpty()

            privacy.errors == ["CCPA consent $invalidCcpa has invalid format: us_privacy must specify 'N' " +
                                       "or 'n', 'Y' or 'y', '-' for the explicit notice" as String]
        }
    }

    def "PBS should apply ccpa when privacy.ccpa.channel-enabled.app or privacy.ccpa.enabled = true in account config"() {
        given: "Default basic generic BidRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def bidRequest = getCcpaBidRequest(DistributionChannel.APP, validCcpa)

        and: "Save account config into DB"
        accountDao.save(getAccountWithCcpa(bidRequest.app.publisher.id, ccpaConfig))

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo == maskGeo(bidRequest)

        where:
        ccpaConfig << [new AccountCcpaConfig(enabled: false, channelEnabled: [(ChannelType.APP): true]),
                       new AccountCcpaConfig(enabled: true)]
    }

    def "PBS should apply ccpa when privacy.ccpa.channel-enabled.web or privacy.ccpa.enabled = true in account config"() {
        given: "Default basic generic BidRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def bidRequest = getCcpaBidRequest(validCcpa)

        and: "Save account config into DB"
        accountDao.save(getAccountWithCcpa(bidRequest.site.publisher.id, ccpaConfig))

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo == maskGeo(bidRequest)

        where:
        ccpaConfig << [new AccountCcpaConfig(enabled: false, channelEnabled: [(WEB): true]),
                       new AccountCcpaConfig(enabled: true)]
    }

    def "PBS should not apply ccpa when privacy.ccpa.channel-enabled.app or privacy.ccpa.enabled = false in account config"() {
        given: "Default basic generic BidRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def bidRequest = getCcpaBidRequest(DistributionChannel.APP, validCcpa)

        and: "Save account config into DB"
        accountDao.save(getAccountWithCcpa(bidRequest.app.publisher.id, ccpaConfig))

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == bidRequest.device.geo.lat
        assert bidderRequests.device?.geo?.lon == bidRequest.device.geo.lon

        where:
        ccpaConfig << [new AccountCcpaConfig(enabled: true, channelEnabled: [(ChannelType.APP): false]),
                       new AccountCcpaConfig(enabled: false)]
    }

    def "PBS should not apply ccpa when privacy.ccpa.channel-enabled.web or privacy.ccpa.enabled = false in account config"() {
        given: "Default basic generic BidRequest"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def bidRequest = getCcpaBidRequest(validCcpa)

        and: "Save account config into DB"
        accountDao.save(getAccountWithCcpa(bidRequest.site.publisher.id, ccpaConfig))

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == bidRequest.device.geo.lat
        assert bidderRequests.device?.geo?.lon == bidRequest.device.geo.lon

        where:
        ccpaConfig << [new AccountCcpaConfig(enabled: true, channelEnabled: [(WEB): false]),
                       new AccountCcpaConfig(enabled: false)]
    }

    def "PBS should recognise 'web' and 'pbjs' as the same channel when privacy.ccpa config is defined in account"() {
        given: "BidRequest with channel: #requestChannel, ccpa"
        def validCcpa = new CcpaConsent(explicitNotice: ENFORCED, optOutSale: ENFORCED)
        def bidRequest = getCcpaBidRequest(validCcpa).tap {
            ext.prebid.channel = new Channel(name: requestChannel)
        }

        and: "Save account config #accountChannel = true with into DB"
        def ccpaConfig = new AccountCcpaConfig(enabled: false, channelEnabled: [(accountChannel): true])
        def account = getAccountWithCcpa(bidRequest.site.publisher.id, ccpaConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

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

    private Account getAccountWithCcpa(String accountId, AccountCcpaConfig ccpaConfig) {
        def privacy = new AccountPrivacyConfig(ccpa: ccpaConfig)
        def accountConfig = new AccountConfig(privacy: privacy)
        new Account(uuid: accountId, config: accountConfig)
    }
}
