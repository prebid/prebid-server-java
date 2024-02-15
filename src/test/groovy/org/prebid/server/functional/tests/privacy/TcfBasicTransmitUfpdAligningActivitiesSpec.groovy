package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.privacy.EnforcementRequirments
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
import static org.prebid.server.functional.model.config.PurposeEnforcement.BASIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID

class TcfBasicTransmitUfpdAligningActivitiesSpec extends PrivacyBaseSpec {

    private static final Map<String, String> PBS_CONFIG = SETTING_CONFIG + GENERIC_VENDOR_CONFIG + GENERIC_COOKIE_SYNC_CONFIG + ["gdpr.vendorlist.v2.http-endpoint-template": null,
                                                                                                                                 "gdpr.vendorlist.v3.http-endpoint-template": null]

    private final PrebidServerService activityPbsServiceExcludeGvl = pbsServiceFactory.getService(PBS_CONFIG)

    private final PrebidServerService activityPbsServiceExcludeGvlWithElderOrtb = pbsServiceFactory.getService(PBS_CONFIG + ["adapters.generic.ortb-version": "2.5"])

    def "PBS should leave the original request with eids data when requireConsent is enabled and #enforcementRequirments.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.ext.prebid.trace = VERBOSE
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments << getCompanyBasedEnforcementRequirments(P4) +
                getLegalBasedEnforcementRequirments(P4) +
                getCompanySoftVendorExceptionsRequirments(P4)
    }

    def "PBS should leave the original request with ext.eids data for elder ortb when requireConsent is enabled and #enforcementRequirments.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.ext.prebid.trace = VERBOSE
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.ext.eids == userEids

        where:
        enforcementRequirments << getCompanyBasedEnforcementRequirments(P4) +
                getLegalBasedEnforcementRequirments(P4) +
                getCompanySoftVendorExceptionsRequirments(P4)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled and #enforcementRequirments.purpose have softVendorExceptions consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [ALIAS.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments << getCompanySoftVendorExceptionsRequirments(P4)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and #enforcementRequirments.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getLegalBasedEnforcementRequirments(P1) +
                getLegalPurposesLITEnforcementRequirments(P1) +
                getCompanyBasedEnforcementRequirments(P1) +
                getLegalBasedEnforcementRequirments(P2) +
                getLegalPurposesLITEnforcementRequirments(P2) +
                getCompanyBasedEnforcementRequirments(P2) +
                getLegalBasedEnforcementRequirments(P3) +
                getLegalPurposesLITEnforcementRequirments(P3) +
                getCompanyBasedEnforcementRequirments(P3) +
                getLegalBasedEnforcementRequirments(P5) +
                getLegalPurposesLITEnforcementRequirments(P5) +
                getCompanyBasedEnforcementRequirments(P5) +
                getLegalBasedEnforcementRequirments(P6) +
                getLegalPurposesLITEnforcementRequirments(P6) +
                getCompanyBasedEnforcementRequirments(P6) +
                getLegalBasedEnforcementRequirments(P7) +
                getLegalPurposesLITEnforcementRequirments(P7) +
                getCompanyBasedEnforcementRequirments(P7) +
                getLegalBasedEnforcementRequirments(P8) +
                getLegalPurposesLITEnforcementRequirments(P8) +
                getCompanyBasedEnforcementRequirments(P8) +
                getLegalBasedEnforcementRequirments(P9) +
                getLegalPurposesLITEnforcementRequirments(P9) +
                getCompanyBasedEnforcementRequirments(P9) +
                getLegalBasedEnforcementRequirments(P10) +
                getLegalPurposesLITEnforcementRequirments(P10) +
                getCompanyBasedEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with ext.eids data for elder ortb when requireConsent is enabled and #enforcementRequirments.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getLegalBasedEnforcementRequirments(P1) +
                getLegalPurposesLITEnforcementRequirments(P1) +
                getCompanyBasedEnforcementRequirments(P1) +
                getLegalBasedEnforcementRequirments(P2) +
                getLegalPurposesLITEnforcementRequirments(P2) +
                getCompanyBasedEnforcementRequirments(P2) +
                getLegalBasedEnforcementRequirments(P3) +
                getLegalPurposesLITEnforcementRequirments(P3) +
                getCompanyBasedEnforcementRequirments(P3) +
                getLegalBasedEnforcementRequirments(P5) +
                getLegalPurposesLITEnforcementRequirments(P5) +
                getCompanyBasedEnforcementRequirments(P5) +
                getLegalBasedEnforcementRequirments(P6) +
                getLegalPurposesLITEnforcementRequirments(P6) +
                getCompanyBasedEnforcementRequirments(P6) +
                getLegalBasedEnforcementRequirments(P7) +
                getLegalPurposesLITEnforcementRequirments(P7) +
                getCompanyBasedEnforcementRequirments(P7) +
                getLegalBasedEnforcementRequirments(P8) +
                getLegalPurposesLITEnforcementRequirments(P8) +
                getCompanyBasedEnforcementRequirments(P8) +
                getLegalBasedEnforcementRequirments(P9) +
                getLegalPurposesLITEnforcementRequirments(P9) +
                getCompanyBasedEnforcementRequirments(P9) +
                getLegalBasedEnforcementRequirments(P10) +
                getLegalPurposesLITEnforcementRequirments(P10) +
                getCompanyBasedEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and #enforcementRequirments.purpose have softVendorExceptions consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [ALIAS.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getCompanySoftVendorExceptionsRequirments(P1) +
                getCompanySoftVendorExceptionsRequirments(P2) +
                getCompanySoftVendorExceptionsRequirments(P3) +
                getCompanySoftVendorExceptionsRequirments(P5) +
                getCompanySoftVendorExceptionsRequirments(P6) +
                getCompanySoftVendorExceptionsRequirments(P7) +
                getCompanySoftVendorExceptionsRequirments(P8) +
                getCompanySoftVendorExceptionsRequirments(P9) +
                getCompanySoftVendorExceptionsRequirments(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled but bidder is excepted and #enforcementRequirments.purpose have any basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments << getLegalBasedEnforcementRequirments(P1) +
                getLegalPurposesLITEnforcementRequirments(P1) +
                getCompanyBasedEnforcementRequirments(P1) +
                getLegalBasedEnforcementRequirments(P2) +
                getLegalPurposesLITEnforcementRequirments(P2) +
                getCompanyBasedEnforcementRequirments(P2) +
                getLegalBasedEnforcementRequirments(P3) +
                getLegalPurposesLITEnforcementRequirments(P3) +
                getCompanyBasedEnforcementRequirments(P3) +
                getLegalBasedEnforcementRequirments(P5) +
                getLegalPurposesLITEnforcementRequirments(P5) +
                getCompanyBasedEnforcementRequirments(P5) +
                getLegalBasedEnforcementRequirments(P6) +
                getLegalPurposesLITEnforcementRequirments(P6) +
                getCompanyBasedEnforcementRequirments(P6) +
                getLegalBasedEnforcementRequirments(P7) +
                getLegalPurposesLITEnforcementRequirments(P7) +
                getCompanyBasedEnforcementRequirments(P7) +
                getLegalBasedEnforcementRequirments(P8) +
                getLegalPurposesLITEnforcementRequirments(P8) +
                getCompanyBasedEnforcementRequirments(P8) +
                getLegalBasedEnforcementRequirments(P9) +
                getLegalPurposesLITEnforcementRequirments(P9) +
                getCompanyBasedEnforcementRequirments(P9) +
                getLegalBasedEnforcementRequirments(P10) +
                getLegalPurposesLITEnforcementRequirments(P10) +
                getCompanyBasedEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled, bidder is excepted and #enforcementRequirments.purpose have unsupported basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getLegalBasedEnforcementRequirments(P1) +
                getCompanyBasedEnforcementRequirments(P1) +
                getLegalPurposesLITEnforcementRequirments(P1) +
                getCompanyBasedEnforcementRequirments(P2) +
                getCompanyBasedEnforcementRequirments(P3) +
                getLegalPurposesLITEnforcementRequirments(P3) +
                getLegalPurposesLITEnforcementRequirments(P4) +
                getCompanyBasedEnforcementRequirments(P5) +
                getLegalPurposesLITEnforcementRequirments(P5) +
                getCompanyBasedEnforcementRequirments(P6) +
                getLegalPurposesLITEnforcementRequirments(P6) +
                getCompanyBasedEnforcementRequirments(P7) +
                getLegalPurposesLITEnforcementRequirments(P7) +
                getCompanyBasedEnforcementRequirments(P8) +
                getLegalPurposesLITEnforcementRequirments(P8) +
                getCompanyBasedEnforcementRequirments(P9) +
                getLegalPurposesLITEnforcementRequirments(P9) +
                getCompanyBasedEnforcementRequirments(P10) +
                getLegalPurposesLITEnforcementRequirments(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is disabled and #enforcementRequirments.purpose have legal basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments << getLegalBasedEnforcementRequirments(P2) +
                getLegalPurposesLITEnforcementRequirments(P2) +
                getLegalBasedEnforcementRequirments(P3) +
                getLegalBasedEnforcementRequirments(P4) +
                getLegalBasedEnforcementRequirments(P5) +
                getLegalBasedEnforcementRequirments(P6) +
                getLegalBasedEnforcementRequirments(P7) +
                getLegalBasedEnforcementRequirments(P8) +
                getLegalBasedEnforcementRequirments(P9) +
                getLegalBasedEnforcementRequirments(P10)
    }

    def "PBS should leave the original request with ext.eids data for elder ortb when requireConsent is disabled and #enforcementRequirments.purpose have legal basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.ext.eids == userEids

        where:
        enforcementRequirments << getLegalBasedEnforcementRequirments(P2) +
                getLegalPurposesLITEnforcementRequirments(P2) +
                getLegalBasedEnforcementRequirments(P3) +
                getLegalBasedEnforcementRequirments(P4) +
                getLegalBasedEnforcementRequirments(P5) +
                getLegalBasedEnforcementRequirments(P6) +
                getLegalBasedEnforcementRequirments(P7) +
                getLegalBasedEnforcementRequirments(P8) +
                getLegalBasedEnforcementRequirments(P9) +
                getLegalBasedEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is disabled and #enforcementRequirments.purpose have softVendorExceptions consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            ext.prebid.aliases = [(ALIAS.value): GENERIC]
            imp[0].ext.prebid.bidder.generic = null
            imp[0].ext.prebid.bidder.alias = new Generic()
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [ALIAS.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getCompanySoftVendorExceptionsRequirments(P1) +
                getCompanySoftVendorExceptionsRequirments(P2) +
                getCompanySoftVendorExceptionsRequirments(P3) +
                getCompanySoftVendorExceptionsRequirments(P5) +
                getCompanySoftVendorExceptionsRequirments(P6) +
                getCompanySoftVendorExceptionsRequirments(P7) +
                getCompanySoftVendorExceptionsRequirments(P8) +
                getCompanySoftVendorExceptionsRequirments(P9) +
                getCompanySoftVendorExceptionsRequirments(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is disabled and #enforcementRequirments.purpose have unsupported basic consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getLegalBasedEnforcementRequirments(P1) +
                getCompanyBasedEnforcementRequirments(P1) +
                getLegalPurposesLITEnforcementRequirments(P1) +
                getCompanyBasedEnforcementRequirments(P2) +
                getCompanyBasedEnforcementRequirments(P3) +
                getLegalPurposesLITEnforcementRequirments(P3) +
                getLegalPurposesLITEnforcementRequirments(P4) +
                getCompanyBasedEnforcementRequirments(P5) +
                getLegalPurposesLITEnforcementRequirments(P5) +
                getCompanyBasedEnforcementRequirments(P6) +
                getLegalPurposesLITEnforcementRequirments(P6) +
                getCompanyBasedEnforcementRequirments(P7) +
                getLegalPurposesLITEnforcementRequirments(P7) +
                getCompanyBasedEnforcementRequirments(P8) +
                getLegalPurposesLITEnforcementRequirments(P8) +
                getCompanyBasedEnforcementRequirments(P9) +
                getLegalPurposesLITEnforcementRequirments(P9) +
                getCompanyBasedEnforcementRequirments(P10) +
                getLegalPurposesLITEnforcementRequirments(P10)
    }

    private static List<EnforcementRequirments> getCompanyBasedEnforcementRequirments(Purpose purpose) {
        [new EnforcementRequirments(purpose: purpose, enforcePurpose: BASIC, purposeConsent: purpose, enforceVendor: false),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: NO, enforceVendor: false),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID)
        ]
    }

    private static List<EnforcementRequirments> getLegalBasedEnforcementRequirments(Purpose purpose) {
        [new EnforcementRequirments(purpose: purpose, enforcePurpose: BASIC, purposeConsent: purpose, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose, vendorExceptions: [GENERIC])
        ]
    }

    private static List<EnforcementRequirments> getCompanySoftVendorExceptionsRequirments(Purpose purpose) {
        [new EnforcementRequirments(purpose: purpose, enforcePurpose: BASIC, purposeConsent: purpose, vendorExceptions: [GENERIC]),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: NO, vendorExceptions: [GENERIC])]
    }

    private static List<EnforcementRequirments> getLegalPurposesLITEnforcementRequirments(Purpose purpose) {
        [new EnforcementRequirments(purpose: purpose, enforcePurpose: BASIC, purposesLITransparency: purpose)]
    }
}
