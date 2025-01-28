package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.privacy.TcfUtils

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
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
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE

class TcfBasicTransmitEidsActivitiesSpec extends PrivacyBaseSpec {

    private static final Map<String, String> PBS_CONFIG = SETTING_CONFIG + GENERIC_VENDOR_CONFIG + GENERIC_CONFIG + ["gdpr.vendorlist.v2.http-endpoint-template": null,
                                                                                                                     "gdpr.vendorlist.v3.http-endpoint-template": null]

    private final PrebidServerService activityPbsServiceExcludeGvl = pbsServiceFactory.getService(PBS_CONFIG)

    def cleanupSpec() {
        pbsServiceFactory.removeContainer(PBS_CONFIG)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled and #enforcementRequirements.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.ext.prebid.trace = VERBOSE
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAdsWithSnakeCase(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
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
        enforcementRequirements << getBasicTcfCompanyBasedEnforcementRequirements(P4) +
                getBasicTcfLegalBasedEnforcementRequirements(P4) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P4)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled and #enforcementRequirements.purpose have softVendorExceptions consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [ALIAS.value])
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

    def "PBS should remove the original request with eids data when requireConsent is enabled and #enforcementRequirements.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendorsSnakeCase: [GENERIC.value])
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
                getBasicTcfCompanyBasedEnforcementRequirements(P1) +
                getBasicTcfLegalBasedEnforcementRequirements(P2) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P2) +
                getBasicTcfCompanyBasedEnforcementRequirements(P2) +
                getBasicTcfLegalBasedEnforcementRequirements(P3) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P3) +
                getBasicTcfCompanyBasedEnforcementRequirements(P3) +
                getBasicTcfLegalBasedEnforcementRequirements(P5) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P5) +
                getBasicTcfCompanyBasedEnforcementRequirements(P5) +
                getBasicTcfLegalBasedEnforcementRequirements(P6) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P6) +
                getBasicTcfCompanyBasedEnforcementRequirements(P6) +
                getBasicTcfLegalBasedEnforcementRequirements(P7) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P7) +
                getBasicTcfCompanyBasedEnforcementRequirements(P7) +
                getBasicTcfLegalBasedEnforcementRequirements(P8) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P8) +
                getBasicTcfCompanyBasedEnforcementRequirements(P8) +
                getBasicTcfLegalBasedEnforcementRequirements(P9) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P9) +
                getBasicTcfCompanyBasedEnforcementRequirements(P9) +
                getBasicTcfLegalBasedEnforcementRequirements(P10) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P10) +
                getBasicTcfCompanyBasedEnforcementRequirements(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and #enforcementRequirements.purpose have softVendorExceptions consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [ALIAS.value])
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

    def "PBS should leave the original request with eids data when requireConsent is enabled but bidder is excepted and #enforcementRequirements.purpose have legal basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
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
                getBasicTcfLegalPurposesLITEnforcementRequirements(P2) +
                getBasicTcfLegalBasedEnforcementRequirements(P3) +
                getBasicTcfLegalBasedEnforcementRequirements(P5) +
                getBasicTcfLegalBasedEnforcementRequirements(P6) +
                getBasicTcfLegalBasedEnforcementRequirements(P7) +
                getBasicTcfLegalBasedEnforcementRequirements(P8) +
                getBasicTcfLegalBasedEnforcementRequirements(P9) +
                getBasicTcfLegalBasedEnforcementRequirements(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled, bidder is excepted and #enforcementRequirements.purpose have unsupported basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
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
                getBasicTcfCompanyBasedEnforcementRequirements(P1) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P1) +
                getBasicTcfCompanyBasedEnforcementRequirements(P2) +
                getBasicTcfCompanyBasedEnforcementRequirements(P3) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P3) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P4) +
                getBasicTcfCompanyBasedEnforcementRequirements(P5) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P5) +
                getBasicTcfCompanyBasedEnforcementRequirements(P6) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P6) +
                getBasicTcfCompanyBasedEnforcementRequirements(P7) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P7) +
                getBasicTcfCompanyBasedEnforcementRequirements(P8) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P8) +
                getBasicTcfCompanyBasedEnforcementRequirements(P9) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P9) +
                getBasicTcfCompanyBasedEnforcementRequirements(P10) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is disabled and #enforcementRequirements.purpose have legal basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
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

    def "PBS should remove the original request with eids data when requireConsent is disabled and #enforcementRequirements.purpose have softVendorExceptions consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [ALIAS.value])
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

    def "PBS should remove the original request with eids data when requireConsent is disabled and #enforcementRequirements.purpose have unsupported basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
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
                getBasicTcfCompanyBasedEnforcementRequirements(P1) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P1) +
                getBasicTcfCompanyBasedEnforcementRequirements(P2) +
                getBasicTcfCompanyBasedEnforcementRequirements(P3) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P3) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P4) +
                getBasicTcfCompanyBasedEnforcementRequirements(P5) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P5) +
                getBasicTcfCompanyBasedEnforcementRequirements(P6) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P6) +
                getBasicTcfCompanyBasedEnforcementRequirements(P7) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P7) +
                getBasicTcfCompanyBasedEnforcementRequirements(P8) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P8) +
                getBasicTcfCompanyBasedEnforcementRequirements(P9) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P9) +
                getBasicTcfCompanyBasedEnforcementRequirements(P10) +
                getBasicTcfLegalPurposesLITEnforcementRequirements(P10)
    }
}
