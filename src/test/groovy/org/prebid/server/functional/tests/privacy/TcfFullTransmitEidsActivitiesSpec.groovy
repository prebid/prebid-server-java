package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.privacy.TcfUtils
import org.testcontainers.images.builder.Transferable
import spock.lang.Shared

import static org.prebid.server.functional.model.config.Purpose.P1
import static org.prebid.server.functional.model.config.Purpose.P2
import static org.prebid.server.functional.model.config.Purpose.P3
import static org.prebid.server.functional.model.config.Purpose.P4
import static org.prebid.server.functional.model.config.Purpose.P5
import static org.prebid.server.functional.model.config.Purpose.P6
import static org.prebid.server.functional.model.config.Purpose.P7
import static org.prebid.server.functional.model.config.Purpose.P8
import static org.prebid.server.functional.model.config.Purpose.P9
import static org.prebid.server.functional.model.config.Purpose.P10
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_EIDS

class TcfFullTransmitEidsActivitiesSpec extends PrivacyBaseSpec {

    @Shared
    private static PrebidServerContainer privacyPbsContainerWithMultipleGvl

    @Shared
    private static PrebidServerService privacyPbsServiceWithMultipleGvl

    def setupSpec() {
        privacyPbsContainerWithMultipleGvl = new PrebidServerContainer(PBS_CONFIG)
        def prepareEncodeResponseBodyWithPurposesOnly = getVendorListContent(true, false, false)
        def prepareEncodeResponseBodyWithLegIntPurposes = getVendorListContent(false, true, false)
        def prepareEncodeResponseBodyWithLegIntAndFlexiblePurposes = getVendorListContent(false, true, true)
        def prepareEncodeResponseBodyWithPurposesAndFlexiblePurposes = getVendorListContent(true, false, true)
        privacyPbsContainerWithMultipleGvl.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithPurposesOnly), getVendorListPath(PURPOSES_ONLY_GVL_VERSION))
        privacyPbsContainerWithMultipleGvl.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithLegIntPurposes), getVendorListPath(LEG_INT_PURPOSES_ONLY_GVL_VERSION))
        privacyPbsContainerWithMultipleGvl.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithLegIntAndFlexiblePurposes), getVendorListPath(LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION))
        privacyPbsContainerWithMultipleGvl.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithPurposesAndFlexiblePurposes), getVendorListPath(PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION))
        privacyPbsContainerWithMultipleGvl.start()
        privacyPbsServiceWithMultipleGvl = new PrebidServerService(privacyPbsContainerWithMultipleGvl)
    }

    def cleanupSpec() {
        privacyPbsContainerWithMultipleGvl.stop()
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled and #enforcementRequirements.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.eids == userEids

        where:
        enforcementRequirements << getFullTcfLegalEnforcementRequirements(P4) + getFullTcfCompanyEnforcementRequirements(P4)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and #enforcementRequirements.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getFullTcfLegalEnforcementRequirements(P1) +
                getFullTcfCompanyEnforcementRequirements(P1) +
                getFullTcfLegalEnforcementRequirements(P2) +
                getFullTcfCompanyEnforcementRequirements(P2) +
                getFullTcfLegalEnforcementRequirements(P3) +
                getFullTcfCompanyEnforcementRequirements(P3) +
                getFullTcfLegalEnforcementRequirements(P5) +
                getFullTcfCompanyEnforcementRequirements(P5) +
                getFullTcfLegalEnforcementRequirements(P6) +
                getFullTcfCompanyEnforcementRequirements(P6) +
                getFullTcfLegalEnforcementRequirements(P7) +
                getFullTcfCompanyEnforcementRequirements(P7) +
                getFullTcfLegalEnforcementRequirements(P8) +
                getFullTcfCompanyEnforcementRequirements(P8) +
                getFullTcfLegalEnforcementRequirements(P9) +
                getFullTcfCompanyEnforcementRequirements(P9) +
                getFullTcfLegalEnforcementRequirements(P10) +
                getFullTcfCompanyEnforcementRequirements(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled but bidder is excepted and #enforcementRequirements.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.eids == userEids

        where:
        enforcementRequirements << getFullTcfLegalEnforcementRequirements(P2) +
                getFullTcfLegalEnforcementRequirements(P3) +
                getFullTcfLegalEnforcementRequirements(P4) +
                getFullTcfLegalEnforcementRequirements(P5) +
                getFullTcfLegalEnforcementRequirements(P6) +
                getFullTcfLegalEnforcementRequirements(P7) +
                getFullTcfLegalEnforcementRequirements(P8) +
                getFullTcfLegalEnforcementRequirements(P9) +
                getFullTcfLegalEnforcementRequirements(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled, bidder is excepted and #enforcementRequirements.purpose have unsupported full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest?.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getFullTcfLegalEnforcementRequirements(P1) +
                getFullTcfCompanyEnforcementRequirements(P1) +
                getFullTcfCompanyEnforcementRequirements(P2) +
                getFullTcfCompanyEnforcementRequirements(P3) +
                getFullTcfCompanyEnforcementRequirements(P5) +
                getFullTcfCompanyEnforcementRequirements(P6) +
                getFullTcfCompanyEnforcementRequirements(P7) +
                getFullTcfCompanyEnforcementRequirements(P8) +
                getFullTcfCompanyEnforcementRequirements(P9) +
                getFullTcfCompanyEnforcementRequirements(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is disabled and #enforcementRequirements.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.eids == userEids

        where:
        enforcementRequirements << getFullTcfLegalEnforcementRequirements(P2) +
                getFullTcfLegalEnforcementRequirements(P3) +
                getFullTcfLegalEnforcementRequirements(P4) +
                getFullTcfLegalEnforcementRequirements(P5) +
                getFullTcfLegalEnforcementRequirements(P6) +
                getFullTcfLegalEnforcementRequirements(P7) +
                getFullTcfLegalEnforcementRequirements(P8) +
                getFullTcfLegalEnforcementRequirements(P9) +
                getFullTcfLegalEnforcementRequirements(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is disabled and #enforcementRequirements.purpose have unsupported full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getFullTcfLegalEnforcementRequirements(P1) +
                getFullTcfCompanyEnforcementRequirements(P1) +
                getFullTcfCompanyEnforcementRequirements(P2) +
                getFullTcfCompanyEnforcementRequirements(P3) +
                getFullTcfCompanyEnforcementRequirements(P5) +
                getFullTcfCompanyEnforcementRequirements(P6) +
                getFullTcfCompanyEnforcementRequirements(P7) +
                getFullTcfCompanyEnforcementRequirements(P8) +
                getFullTcfCompanyEnforcementRequirements(P9) +
                getFullTcfCompanyEnforcementRequirements(P10)
    }
}
