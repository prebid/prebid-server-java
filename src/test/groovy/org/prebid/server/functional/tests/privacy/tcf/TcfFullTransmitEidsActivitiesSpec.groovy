package org.prebid.server.functional.tests.privacy.tcf

import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.util.privacy.TcfUtils

import static org.prebid.server.functional.model.config.Purpose.P1
import static org.prebid.server.functional.model.config.Purpose.P10
import static org.prebid.server.functional.model.config.Purpose.P2
import static org.prebid.server.functional.model.config.Purpose.P3
import static org.prebid.server.functional.model.config.Purpose.P4
import static org.prebid.server.functional.model.config.Purpose.P5
import static org.prebid.server.functional.model.config.Purpose.P6
import static org.prebid.server.functional.model.config.Purpose.P7
import static org.prebid.server.functional.model.config.Purpose.P8
import static org.prebid.server.functional.model.config.Purpose.P9
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_EIDS

class TcfFullTransmitEidsActivitiesSpec extends TcfBaseSpec {

    def "PBS should preserve eids from original request when requireConsent is enabled and #enforcementRequirements.purpose have full consent"() {
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
        enforcementRequirements << getFullTcfLegalEnforcementRequirements(P4) +
                getFullTcfCompanyEnforcementRequirements(P4)
    }

    def "PBS should not transmit eids from original request when #enforcementRequirements.purpose have legitimate interests consent"() {
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
        enforcementRequirements << getFullTcfLegalLegitimateInterestsRequirements(P4) +
                getFullTcfCompanyLegitimateInterestsRequirements(P4)
    }

    def "PBS should not transmit eids from original request when requireConsent is enabled and #enforcementRequirements.purpose have full consent"() {
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
        enforcementRequirements <<
                getFullTcfLegalEnforcementRequirements(P1) +
                getFullTcfLegalEnforcementRequirements(P2) +
                getFullTcfLegalEnforcementRequirements(P3) +
                getFullTcfLegalEnforcementRequirements(P5) +
                getFullTcfLegalEnforcementRequirements(P6) +
                getFullTcfLegalEnforcementRequirements(P7) +
                getFullTcfLegalEnforcementRequirements(P8) +
                getFullTcfLegalEnforcementRequirements(P9) +
                getFullTcfLegalEnforcementRequirements(P10) +

                getFullTcfCompanyEnforcementRequirements(P1) +
                getFullTcfCompanyEnforcementRequirements(P2) +
                getFullTcfCompanyEnforcementRequirements(P3) +
                getFullTcfCompanyEnforcementRequirements(P5) +
                getFullTcfCompanyEnforcementRequirements(P6) +
                getFullTcfCompanyEnforcementRequirements(P7) +
                getFullTcfCompanyEnforcementRequirements(P8) +
                getFullTcfCompanyEnforcementRequirements(P9) +
                getFullTcfCompanyEnforcementRequirements(P10) +

                getFullTcfLegalLegitimateInterestsRequirements(P1) +
                getFullTcfLegalLegitimateInterestsRequirements(P2) +
                getFullTcfLegalLegitimateInterestsRequirements(P3) +
                getFullTcfLegalLegitimateInterestsRequirements(P5) +
                getFullTcfLegalLegitimateInterestsRequirements(P6) +
                getFullTcfLegalLegitimateInterestsRequirements(P7) +
                getFullTcfLegalLegitimateInterestsRequirements(P8) +
                getFullTcfLegalLegitimateInterestsRequirements(P9) +
                getFullTcfLegalLegitimateInterestsRequirements(P10) +

                getFullTcfCompanyLegitimateInterestsRequirements(P1) +
                getFullTcfCompanyLegitimateInterestsRequirements(P2) +
                getFullTcfCompanyLegitimateInterestsRequirements(P3) +
                getFullTcfCompanyLegitimateInterestsRequirements(P5) +
                getFullTcfCompanyLegitimateInterestsRequirements(P6) +
                getFullTcfCompanyLegitimateInterestsRequirements(P7) +
                getFullTcfCompanyLegitimateInterestsRequirements(P8) +
                getFullTcfCompanyLegitimateInterestsRequirements(P9) +
                getFullTcfCompanyLegitimateInterestsRequirements(P10)
    }

    def "PBS should preserve eids from original request when requireConsent is enabled but bidder is excepted and #enforcementRequirements.purpose have full consent"() {
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
        enforcementRequirements <<
                getFullTcfLegalEnforcementRequirements(P2) +
                getFullTcfLegalEnforcementRequirements(P3) +
                getFullTcfLegalEnforcementRequirements(P4) +
                getFullTcfLegalEnforcementRequirements(P5) +
                getFullTcfLegalEnforcementRequirements(P6) +
                getFullTcfLegalEnforcementRequirements(P7) +
                getFullTcfLegalEnforcementRequirements(P8) +
                getFullTcfLegalEnforcementRequirements(P9) +
                getFullTcfLegalEnforcementRequirements(P10) +

                getFullTcfLegalLegitimateInterestsRequirements(P2) +
                getFullTcfLegalLegitimateInterestsRequirements(P7) +
                getFullTcfLegalLegitimateInterestsRequirements(P8) +
                getFullTcfLegalLegitimateInterestsRequirements(P9) +
                getFullTcfLegalLegitimateInterestsRequirements(P10)
    }

    def "PBS should not transmit eids from original request when requireConsent is enabled, bidder is excepted and #enforcementRequirements.purpose have unsupported full consent"() {
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
                getFullTcfCompanyEnforcementRequirements(P10) +

                getFullTcfCompanyLegitimateInterestsRequirements(P1) +
                getFullTcfCompanyLegitimateInterestsRequirements(P2) +
                getFullTcfCompanyLegitimateInterestsRequirements(P3) +
                getFullTcfCompanyLegitimateInterestsRequirements(P5) +
                getFullTcfCompanyLegitimateInterestsRequirements(P6) +
                getFullTcfCompanyLegitimateInterestsRequirements(P7) +
                getFullTcfCompanyLegitimateInterestsRequirements(P8) +
                getFullTcfCompanyLegitimateInterestsRequirements(P9) +
                getFullTcfCompanyLegitimateInterestsRequirements(P10)
    }

    def "PBS should preserve eids from original request when requireConsent is disabled and #enforcementRequirements.purpose have full consent"() {
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
        enforcementRequirements <<
                getFullTcfLegalEnforcementRequirements(P2) +
                getFullTcfLegalEnforcementRequirements(P3) +
                getFullTcfLegalEnforcementRequirements(P4) +
                getFullTcfLegalEnforcementRequirements(P5) +
                getFullTcfLegalEnforcementRequirements(P6) +
                getFullTcfLegalEnforcementRequirements(P7) +
                getFullTcfLegalEnforcementRequirements(P8) +
                getFullTcfLegalEnforcementRequirements(P9) +
                getFullTcfLegalEnforcementRequirements(P10) +

                getFullTcfLegalLegitimateInterestsRequirements(P2) +
                getFullTcfLegalLegitimateInterestsRequirements(P7) +
                getFullTcfLegalLegitimateInterestsRequirements(P8) +
                getFullTcfLegalLegitimateInterestsRequirements(P9) +
                getFullTcfLegalLegitimateInterestsRequirements(P10)
    }

    def "PBS should not transmit eids from original request when requireConsent is disabled and #enforcementRequirements.purpose have unsupported full consent"() {
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
                getFullTcfCompanyEnforcementRequirements(P10) +

                getFullTcfCompanyLegitimateInterestsRequirements(P1) +
                getFullTcfCompanyLegitimateInterestsRequirements(P2) +
                getFullTcfCompanyLegitimateInterestsRequirements(P3) +
                getFullTcfCompanyLegitimateInterestsRequirements(P5) +
                getFullTcfCompanyLegitimateInterestsRequirements(P6) +
                getFullTcfCompanyLegitimateInterestsRequirements(P7) +
                getFullTcfCompanyLegitimateInterestsRequirements(P8) +
                getFullTcfCompanyLegitimateInterestsRequirements(P9) +
                getFullTcfCompanyLegitimateInterestsRequirements(P10)
    }

    def "PBS should not transmit eids when only legitimate interest is present for TCF 2.3"() {
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
        assert !bidderRequest?.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements <<
                getFullTcfLegalLegitimateInterestsRequirements(P3) +
                getFullTcfLegalLegitimateInterestsRequirements(P4) +
                getFullTcfLegalLegitimateInterestsRequirements(P5) +
                getFullTcfLegalLegitimateInterestsRequirements(P6) +

                getFullTcfCompanyLegitimateInterestsRequirements(P3) +
                getFullTcfCompanyLegitimateInterestsRequirements(P4) +
                getFullTcfCompanyLegitimateInterestsRequirements(P5) +
                getFullTcfCompanyLegitimateInterestsRequirements(P6)
    }
}
