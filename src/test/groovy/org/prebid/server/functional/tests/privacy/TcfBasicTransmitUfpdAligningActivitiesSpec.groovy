package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.privacy.EnforcementRequirments
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.util.privacy.TcfUtils
import spock.lang.PendingFeature

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
import static org.prebid.server.functional.model.config.PurposeEnforcement.BASIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.TraceLevel.VERBOSE
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.testcontainers.Dependencies.getNetworkServiceContainer
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID

class TcfBasicTransmitUfpdAligningActivitiesSpec extends PrivacyBaseSpec {

    private static final Map<String, String> PBS_CONFIG = SETTING_CONFIG + GENERIC_VENDOR_CONFIG + GENERIC_COOKIE_SYNC_CONFIG + ["gdpr.vendorlist.v2.http-endpoint-template": null,
                                                                                                                                 "gdpr.vendorlist.v3.http-endpoint-template": null]

    private final PrebidServerService activityPbsServiceExcludeGvl = pbsServiceFactory.getService(PBS_CONFIG)

    def "PBS should leave the original request with eids data when requireConsent is enabled and #enforcementRequirments.purpose have basic consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.ext.prebid.trace = VERBOSE
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        // Basic Ads required for bidder call, should be ignored for testing other exceptions
        purposes[P2] = new PurposeConfig(vendorExceptions: [GENERIC.value])
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments << getBasicEnforcementRequirments(P4)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and #enforcementRequirments.purpose have basic consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
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

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getBasicEnforcementRequirments(P2) + getBasicPurposesLITEnforcementRequirments(P2)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled but bidder is excepted and #enforcementRequirments.purpose have basic consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def requestEids = userEids + userExtEids
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, requestEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments << getBasicEnforcementRequirments(P2) + getBasicPurposesLITEnforcementRequirments(P2)
    }

    @PendingFeature(reason = "can't separate purposes from basic ads")
    def "PBS should remove the original request with eids data when requireConsent is enabled, bidder is excepted and #enforcementRequirments.purpose have unsupported basic consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def requestEids = userEids + userExtEids
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, requestEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes, basicEnforcementVendors: [GENERIC.value])
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsServiceExcludeGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getBasicEnforcementRequirments(P1) +
                getBasicPurposesLITEnforcementRequirments(P3) +
                getBasicPurposesLITEnforcementRequirments(P4) +
                getBasicPurposesLITEnforcementRequirments(P5) +
                getBasicPurposesLITEnforcementRequirments(P6) +
                getBasicPurposesLITEnforcementRequirments(P7) +
                getBasicPurposesLITEnforcementRequirments(P8) +
                getBasicPurposesLITEnforcementRequirments(P9) +
                getBasicPurposesLITEnforcementRequirments(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is disabled and #enforcementRequirments.purpose have basic consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
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

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments <<
                getBasicEnforcementRequirments(P2) +
                getBasicPurposesLITEnforcementRequirments(P2)
    }

    @PendingFeature(reason = "can't separate purposes from basic ads")
    def "PBS should remove the original request with eids data when requireConsent is disabled and #enforcementRequirments.purpose have unsupported basic consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
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

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getBasicEnforcementRequirments(P1) +
                getBasicPurposesLITEnforcementRequirments(P3) +
                getBasicPurposesLITEnforcementRequirments(P4) +
                getBasicPurposesLITEnforcementRequirments(P5) +
                getBasicPurposesLITEnforcementRequirments(P6) +
                getBasicPurposesLITEnforcementRequirments(P7) +
                getBasicPurposesLITEnforcementRequirments(P8) +
                getBasicPurposesLITEnforcementRequirments(P9) +
                getBasicPurposesLITEnforcementRequirments(P10)
    }

    private static List<EnforcementRequirments> getBasicEnforcementRequirments(Purpose purpose) {
        [
         new EnforcementRequirments(purpose: purpose, enforcePurpose: BASIC, purposeConsent: purpose, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: BASIC, purposeConsent: purpose, enforceVendor: false),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: NO, enforceVendor: false),
         new EnforcementRequirments(purpose: purpose, vendorExceptions: [GENERIC]),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: BASIC, purposeConsent: purpose, softVendorExceptions: [GENERIC]),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: NO, softVendorExceptions: [GENERIC])]
    }

    private static List<EnforcementRequirments> getBasicPurposesLITEnforcementRequirments(Purpose purpose) {
        [new EnforcementRequirments(purpose: purpose, enforcePurpose: BASIC, purposesLITransparency: purpose)]
    }
}
