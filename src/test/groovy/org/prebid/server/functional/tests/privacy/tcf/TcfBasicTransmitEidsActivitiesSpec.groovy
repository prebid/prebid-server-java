package org.prebid.server.functional.tests.privacy.tcf

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.util.privacy.TcfUtils

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
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
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class TcfBasicTransmitEidsActivitiesSpec extends TcfBaseSpec {

    def "PBS should preserve eids from original request when requireConsent is enabled and #enforcementRequirements.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.ext.prebid.trace = VERBOSE
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAdsWithSnakeCase(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.eids == userEids

        where:
        enforcementRequirements <<
                getBasicTcfLegalBasedEnforcementRequirements(P4) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P4)
    }

    def "PBS should preserve eids from original request when requireConsent is enabled and #enforcementRequirements.purpose have softVendorExceptions consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [ALIAS])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.eids == userEids

        where:
        enforcementRequirements << getBasicTcfCompanySoftVendorExceptionsRequirements(P4)
    }

    def "PBS should not transmit eids from original request when requireConsent is enabled and #enforcementRequirements.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendorsSnakeCase: [GENERIC])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getBasicTcfLegalBasedEnforcementRequirements(P1) +
                getBasicTcfLegalBasedEnforcementRequirements(P2) +
                getBasicTcfLegalBasedEnforcementRequirements(P3) +
                getBasicTcfLegalBasedEnforcementRequirements(P5) +
                getBasicTcfLegalBasedEnforcementRequirements(P6) +
                getBasicTcfLegalBasedEnforcementRequirements(P7) +
                getBasicTcfLegalBasedEnforcementRequirements(P8) +
                getBasicTcfLegalBasedEnforcementRequirements(P9) +
                getBasicTcfLegalBasedEnforcementRequirements(P10) +

                getBasicTcfLegalPurposesLITEnforcementRequirements(P1) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P2) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P3) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P5) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P6) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P7) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P8) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P9) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P10) +

                getBasicTcfCompanyBasedEnforcementRequirements(P1) +
                getBasicTcfCompanyBasedEnforcementRequirements(P2) +
                getBasicTcfCompanyBasedEnforcementRequirements(P3) +
                getBasicTcfCompanyBasedEnforcementRequirements(P5) +
                getBasicTcfCompanyBasedEnforcementRequirements(P6) +
                getBasicTcfCompanyBasedEnforcementRequirements(P7) +
                getBasicTcfCompanyBasedEnforcementRequirements(P8) +
                getBasicTcfCompanyBasedEnforcementRequirements(P9) +
                getBasicTcfCompanyBasedEnforcementRequirements(P10)
    }

    def "PBS should not transmit eids from original request when requireConsent is enabled and #enforcementRequirements.purpose have softVendorExceptions consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [ALIAS])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getBasicTcfCompanySoftVendorExceptionsRequirements(P1) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P2) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P3) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P5) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P6) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P7) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P8) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P9) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P10)
    }

    def "PBS should preserve eids from original request when requireConsent is enabled but bidder is excepted and #enforcementRequirements.purpose have legal basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.eids == userEids

        where:
        enforcementRequirements <<
                getBasicTcfLegalBasedEnforcementRequirements(P2) +
                getBasicTcfLegalBasedEnforcementRequirements(P3) +
                getBasicTcfLegalBasedEnforcementRequirements(P5) +
                getBasicTcfLegalBasedEnforcementRequirements(P6) +
                getBasicTcfLegalBasedEnforcementRequirements(P7) +
                getBasicTcfLegalBasedEnforcementRequirements(P8) +
                getBasicTcfLegalBasedEnforcementRequirements(P9) +
                getBasicTcfLegalBasedEnforcementRequirements(P10) +

                getBasicTcfLegalPurposesLITEnforcementRequirements(P2)
    }

    def "PBS should not transmit eids from original request when requireConsent is enabled, bidder is excepted and #enforcementRequirements.purpose have unsupported basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getBasicTcfLegalBasedEnforcementRequirements(P1) +

                getBasicTcfLegalPurposesLITEnforcementRequirements(P1) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P3) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P4) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P5) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P6) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P7) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P8) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P9) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P10) +

                getBasicTcfCompanyBasedEnforcementRequirements(P1) +
                getBasicTcfCompanyBasedEnforcementRequirements(P2) +
                getBasicTcfCompanyBasedEnforcementRequirements(P3) +
                getBasicTcfCompanyBasedEnforcementRequirements(P5) +
                getBasicTcfCompanyBasedEnforcementRequirements(P6) +
                getBasicTcfCompanyBasedEnforcementRequirements(P7) +
                getBasicTcfCompanyBasedEnforcementRequirements(P8) +
                getBasicTcfCompanyBasedEnforcementRequirements(P9) +
                getBasicTcfCompanyBasedEnforcementRequirements(P10)
    }

    def "PBS should preserve eids from original request when requireConsent is disabled and #enforcementRequirements.purpose have legal basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.eids == userEids

        where:
        enforcementRequirements << getBasicTcfLegalBasedEnforcementRequirements(P2) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P2) +
                getBasicTcfLegalBasedEnforcementRequirements(P3) +
                getBasicTcfLegalBasedEnforcementRequirements(P4) +
                getBasicTcfLegalBasedEnforcementRequirements(P5) +
                getBasicTcfLegalBasedEnforcementRequirements(P6) +
                getBasicTcfLegalBasedEnforcementRequirements(P7) +
                getBasicTcfLegalBasedEnforcementRequirements(P8) +
                getBasicTcfLegalBasedEnforcementRequirements(P9) +
                getBasicTcfLegalBasedEnforcementRequirements(P10)
    }

    def "PBS should not transmit eids from original request when requireConsent is disabled and #enforcementRequirements.purpose have softVendorExceptions consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [ALIAS])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getBasicTcfCompanySoftVendorExceptionsRequirements(P1) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P2) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P3) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P5) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P6) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P7) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P8) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P9) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P10)
    }

    def "PBS should not transmit eids from original request when requireConsent is disabled and #enforcementRequirements.purpose have unsupported basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest?.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getBasicTcfLegalBasedEnforcementRequirements(P1) +

                getBasicTcfLegalPurposesLITEnforcementRequirements(P1) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P3) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P4) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P5) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P6) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P7) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P8) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P9) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P10) +

                getBasicTcfCompanyBasedEnforcementRequirements(P1) +
                getBasicTcfCompanyBasedEnforcementRequirements(P2) +
                getBasicTcfCompanyBasedEnforcementRequirements(P3) +
                getBasicTcfCompanyBasedEnforcementRequirements(P5) +
                getBasicTcfCompanyBasedEnforcementRequirements(P6) +
                getBasicTcfCompanyBasedEnforcementRequirements(P7) +
                getBasicTcfCompanyBasedEnforcementRequirements(P8) +
                getBasicTcfCompanyBasedEnforcementRequirements(P9) +
                getBasicTcfCompanyBasedEnforcementRequirements(P10)
    }
}
