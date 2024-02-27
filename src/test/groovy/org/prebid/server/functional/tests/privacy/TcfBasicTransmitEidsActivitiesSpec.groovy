package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.bidder.Rubicon
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.privacy.EnforcementRequirement
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.privacy.TcfUtils
import spock.lang.IgnoreRest

import static org.prebid.server.functional.model.bidder.BidderName.ALIAS
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.bidder.BidderName.RUBICON
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
import static org.prebid.server.functional.model.config.PurposeEnforcement.BASIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_EIDS
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID

class TcfBasicTransmitEidsActivitiesSpec extends PrivacyBaseSpec {

    private static final Map<String, String> PBS_CONFIG = SETTING_CONFIG + GENERIC_VENDOR_CONFIG + GENERIC_COOKIE_SYNC_CONFIG + ["gdpr.vendorlist.v2.http-endpoint-template": null,
                                                                                                                                 "gdpr.vendorlist.v3.http-endpoint-template": null]

    private final PrebidServerService activityPbsServiceExcludeGvl = pbsServiceFactory.getService(PBS_CONFIG)

    private final PrebidServerService activityPbsServiceExcludeGvlWithElderOrtb = pbsServiceFactory.getService(PBS_CONFIG + ["adapters.generic.ortb-version": "2.5"])

    def "PBS should leave the original request with eids data when requireConsent is enabled and #enforcementRequirements.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.ext.prebid.trace = VERBOSE
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
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
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirements << getCompanyBasedEnforcementRequirements(P4) +
                getLegalBasedEnforcementRequirements(P4) +
                getCompanySoftVendorExceptionsRequirements(P4)
    }

    def "PBS should leave the original request with ext.eids data for elder ortb when requireConsent is enabled and #enforcementRequirements.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.ext.prebid.trace = VERBOSE
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.ext.eids == userEids

        where:
        enforcementRequirements << getCompanyBasedEnforcementRequirements(P4) +
                getLegalBasedEnforcementRequirements(P4) +
                getCompanySoftVendorExceptionsRequirements(P4)
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
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirements << getCompanySoftVendorExceptionsRequirements(P4)
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
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getLegalBasedEnforcementRequirements(P1) +
                getLegalPurposesLITEnforcementRequirements(P1) +
                getCompanyBasedEnforcementRequirements(P1) +
                getLegalBasedEnforcementRequirements(P2) +
                getLegalPurposesLITEnforcementRequirements(P2) +
                getCompanyBasedEnforcementRequirements(P2) +
                getLegalBasedEnforcementRequirements(P3) +
                getLegalPurposesLITEnforcementRequirements(P3) +
                getCompanyBasedEnforcementRequirements(P3) +
                getLegalBasedEnforcementRequirements(P5) +
                getLegalPurposesLITEnforcementRequirements(P5) +
                getCompanyBasedEnforcementRequirements(P5) +
                getLegalBasedEnforcementRequirements(P6) +
                getLegalPurposesLITEnforcementRequirements(P6) +
                getCompanyBasedEnforcementRequirements(P6) +
                getLegalBasedEnforcementRequirements(P7) +
                getLegalPurposesLITEnforcementRequirements(P7) +
                getCompanyBasedEnforcementRequirements(P7) +
                getLegalBasedEnforcementRequirements(P8) +
                getLegalPurposesLITEnforcementRequirements(P8) +
                getCompanyBasedEnforcementRequirements(P8) +
                getLegalBasedEnforcementRequirements(P9) +
                getLegalPurposesLITEnforcementRequirements(P9) +
                getCompanyBasedEnforcementRequirements(P9) +
                getLegalBasedEnforcementRequirements(P10) +
                getLegalPurposesLITEnforcementRequirements(P10) +
                getCompanyBasedEnforcementRequirements(P10)
    }

    def "PBS should remove the original request with ext.eids data for elder ortb when requireConsent is enabled and #enforcementRequirements.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirements)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirements, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_EIDS, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getLegalBasedEnforcementRequirements(P1) +
                getLegalPurposesLITEnforcementRequirements(P1) +
                getCompanyBasedEnforcementRequirements(P1) +
                getLegalBasedEnforcementRequirements(P2) +
                getLegalPurposesLITEnforcementRequirements(P2) +
                getCompanyBasedEnforcementRequirements(P2) +
                getLegalBasedEnforcementRequirements(P3) +
                getLegalPurposesLITEnforcementRequirements(P3) +
                getCompanyBasedEnforcementRequirements(P3) +
                getLegalBasedEnforcementRequirements(P5) +
                getLegalPurposesLITEnforcementRequirements(P5) +
                getCompanyBasedEnforcementRequirements(P5) +
                getLegalBasedEnforcementRequirements(P6) +
                getLegalPurposesLITEnforcementRequirements(P6) +
                getCompanyBasedEnforcementRequirements(P6) +
                getLegalBasedEnforcementRequirements(P7) +
                getLegalPurposesLITEnforcementRequirements(P7) +
                getCompanyBasedEnforcementRequirements(P7) +
                getLegalBasedEnforcementRequirements(P8) +
                getLegalPurposesLITEnforcementRequirements(P8) +
                getCompanyBasedEnforcementRequirements(P8) +
                getLegalBasedEnforcementRequirements(P9) +
                getLegalPurposesLITEnforcementRequirements(P9) +
                getCompanyBasedEnforcementRequirements(P9) +
                getLegalBasedEnforcementRequirements(P10) +
                getLegalPurposesLITEnforcementRequirements(P10) +
                getCompanyBasedEnforcementRequirements(P10)
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
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getCompanySoftVendorExceptionsRequirements(P1) +
                getCompanySoftVendorExceptionsRequirements(P2) +
                getCompanySoftVendorExceptionsRequirements(P3) +
                getCompanySoftVendorExceptionsRequirements(P5) +
                getCompanySoftVendorExceptionsRequirements(P6) +
                getCompanySoftVendorExceptionsRequirements(P7) +
                getCompanySoftVendorExceptionsRequirements(P8) +
                getCompanySoftVendorExceptionsRequirements(P9) +
                getCompanySoftVendorExceptionsRequirements(P10)
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
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirements <<
                getLegalBasedEnforcementRequirements(P2) +
                getLegalPurposesLITEnforcementRequirements(P2) +
                getLegalBasedEnforcementRequirements(P3) +
                getLegalBasedEnforcementRequirements(P5) +
                getLegalBasedEnforcementRequirements(P6) +
                getLegalBasedEnforcementRequirements(P7) +
                getLegalBasedEnforcementRequirements(P8) +
                getLegalBasedEnforcementRequirements(P9) +
                getLegalBasedEnforcementRequirements(P10)
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
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getLegalBasedEnforcementRequirements(P1) +
                getCompanyBasedEnforcementRequirements(P1) +
                getLegalPurposesLITEnforcementRequirements(P1) +
                getCompanyBasedEnforcementRequirements(P2) +
                getCompanyBasedEnforcementRequirements(P3) +
                getLegalPurposesLITEnforcementRequirements(P3) +
                getLegalPurposesLITEnforcementRequirements(P4) +
                getCompanyBasedEnforcementRequirements(P5) +
                getLegalPurposesLITEnforcementRequirements(P5) +
                getCompanyBasedEnforcementRequirements(P6) +
                getLegalPurposesLITEnforcementRequirements(P6) +
                getCompanyBasedEnforcementRequirements(P7) +
                getLegalPurposesLITEnforcementRequirements(P7) +
                getCompanyBasedEnforcementRequirements(P8) +
                getLegalPurposesLITEnforcementRequirements(P8) +
                getCompanyBasedEnforcementRequirements(P9) +
                getLegalPurposesLITEnforcementRequirements(P9) +
                getCompanyBasedEnforcementRequirements(P10) +
                getLegalPurposesLITEnforcementRequirements(P10)
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
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirements << getLegalBasedEnforcementRequirements(P2) +
                getLegalPurposesLITEnforcementRequirements(P2) +
                getLegalBasedEnforcementRequirements(P3) +
                getLegalBasedEnforcementRequirements(P4) +
                getLegalBasedEnforcementRequirements(P5) +
                getLegalBasedEnforcementRequirements(P6) +
                getLegalBasedEnforcementRequirements(P7) +
                getLegalBasedEnforcementRequirements(P8) +
                getLegalBasedEnforcementRequirements(P9) +
                getLegalBasedEnforcementRequirements(P10)
    }

    def "PBS should leave the original request with ext.eids data for elder ortb when requireConsent is disabled and #enforcementRequirements.purpose have legal basic consent"() {
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
        activityPbsServiceExcludeGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.ext.eids == userEids

        where:
        enforcementRequirements << getLegalBasedEnforcementRequirements(P2) +
                getLegalPurposesLITEnforcementRequirements(P2) +
                getLegalBasedEnforcementRequirements(P3) +
                getLegalBasedEnforcementRequirements(P4) +
                getLegalBasedEnforcementRequirements(P5) +
                getLegalBasedEnforcementRequirements(P6) +
                getLegalBasedEnforcementRequirements(P7) +
                getLegalBasedEnforcementRequirements(P8) +
                getLegalBasedEnforcementRequirements(P9) +
                getLegalBasedEnforcementRequirements(P10)
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
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getCompanySoftVendorExceptionsRequirements(P1) +
                getCompanySoftVendorExceptionsRequirements(P2) +
                getCompanySoftVendorExceptionsRequirements(P3) +
                getCompanySoftVendorExceptionsRequirements(P5) +
                getCompanySoftVendorExceptionsRequirements(P6) +
                getCompanySoftVendorExceptionsRequirements(P7) +
                getCompanySoftVendorExceptionsRequirements(P8) +
                getCompanySoftVendorExceptionsRequirements(P9) +
                getCompanySoftVendorExceptionsRequirements(P10)
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
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirements << getLegalBasedEnforcementRequirements(P1) +
                getCompanyBasedEnforcementRequirements(P1) +
                getLegalPurposesLITEnforcementRequirements(P1) +
                getCompanyBasedEnforcementRequirements(P2) +
                getCompanyBasedEnforcementRequirements(P3) +
                getLegalPurposesLITEnforcementRequirements(P3) +
                getLegalPurposesLITEnforcementRequirements(P4) +
                getCompanyBasedEnforcementRequirements(P5) +
                getLegalPurposesLITEnforcementRequirements(P5) +
                getCompanyBasedEnforcementRequirements(P6) +
                getLegalPurposesLITEnforcementRequirements(P6) +
                getCompanyBasedEnforcementRequirements(P7) +
                getLegalPurposesLITEnforcementRequirements(P7) +
                getCompanyBasedEnforcementRequirements(P8) +
                getLegalPurposesLITEnforcementRequirements(P8) +
                getCompanyBasedEnforcementRequirements(P9) +
                getLegalPurposesLITEnforcementRequirements(P9) +
                getCompanyBasedEnforcementRequirements(P10) +
                getLegalPurposesLITEnforcementRequirements(P10)
    }

    private static List<EnforcementRequirement> getCompanyBasedEnforcementRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, purposeConsent: purpose, enforceVendor: false),
         new EnforcementRequirement(purpose: purpose, enforcePurpose: NO, enforceVendor: false),
         new EnforcementRequirement(purpose: purpose, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID)
        ]
    }

    private static List<EnforcementRequirement> getLegalBasedEnforcementRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, purposeConsent: purpose, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirement(purpose: purpose, vendorExceptions: [GENERIC])
        ]
    }

    private static List<EnforcementRequirement> getCompanySoftVendorExceptionsRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, purposeConsent: purpose, vendorExceptions: [GENERIC]),
         new EnforcementRequirement(purpose: purpose, enforcePurpose: NO, vendorExceptions: [GENERIC])]
    }

    private static List<EnforcementRequirement> getLegalPurposesLITEnforcementRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, purposesLITransparency: purpose)]
    }
}
