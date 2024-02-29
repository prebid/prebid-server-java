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

class PrivacyOrtbConverterSpec extends PrivacyBaseSpec {

    private static final Map<String, String> PBS_CONFIG = SETTING_CONFIG + GENERIC_VENDOR_CONFIG + GENERIC_COOKIE_SYNC_CONFIG + ["gdpr.vendorlist.v2.http-endpoint-template": null,
                                                                                                                                 "gdpr.vendorlist.v3.http-endpoint-template": null]
    private final PrebidServerService activityPbsServiceExcludeGvlWithElderOrtb = pbsServiceFactory.getService(PBS_CONFIG + ["adapters.generic.ortb-version": "2.5"])

    @Shared
    private static PrebidServerService privacyPbsServiceWithMultipleGvlWithElderOrtb

    @Shared
    private static PrebidServerContainer privacyPbsContainerWithMultipleGvlWithElderOrtb

    def setupSpec() {
        def prepareEncodeResponseBodyWithPurposesOnly = getVendorListContent(true, false, false)
        def prepareEncodeResponseBodyWithLegIntPurposes = getVendorListContent(false, true, false)
        def prepareEncodeResponseBodyWithLegIntAndFlexiblePurposes = getVendorListContent(false, true, true)
        def prepareEncodeResponseBodyWithPurposesAndFlexiblePurposes = getVendorListContent(true, false, true)
        privacyPbsContainerWithMultipleGvlWithElderOrtb = new PrebidServerContainer(PBS_CONFIG + ["adapters.generic.ortb-version": "2.5"])
        privacyPbsContainerWithMultipleGvlWithElderOrtb.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithPurposesOnly), getVendorListPath(PURPOSES_ONLY_GVL_VERSION))
        privacyPbsContainerWithMultipleGvlWithElderOrtb.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithLegIntPurposes), getVendorListPath(LEG_INT_PURPOSES_ONLY_GVL_VERSION))
        privacyPbsContainerWithMultipleGvlWithElderOrtb.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithLegIntAndFlexiblePurposes), getVendorListPath(LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION))
        privacyPbsContainerWithMultipleGvlWithElderOrtb.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithPurposesAndFlexiblePurposes), getVendorListPath(PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION))
        privacyPbsContainerWithMultipleGvlWithElderOrtb.start()
        privacyPbsServiceWithMultipleGvlWithElderOrtb = new PrebidServerService(privacyPbsContainerWithMultipleGvlWithElderOrtb)
    }

    def cleanupSpec() {
        privacyPbsContainerWithMultipleGvlWithElderOrtb.stop()
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
        assert bidderRequest?.user?.ext?.eids == userEids

        where:
        enforcementRequirements << getBasicTcfCompanyBasedEnforcementRequirements(P4) +
                getBasicTcfLegalBasedEnforcementRequirements(P4) +
                getBasicTcfCompanySoftVendorExceptionsRequirements(P4)
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
        assert bidderRequest?.user?.ext?.eids == userEids

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

    def "PBS should leave the original request with ext.eids data for elder ortb when requireConsent is enabled and #enforcementRequirements.purpose have full consent"() {
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
        privacyPbsServiceWithMultipleGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.ext?.eids == userEids

        where:
        enforcementRequirements << getFullTcfLegalEnforcementRequirements(P4) + getFullTcfCompanyEnforcementRequirements(P4)
    }

    def "PBS should remove the original request with ext.eids data for elder ortb when requireConsent is enabled and #enforcementRequirements.purpose have full consent"() {
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
        privacyPbsServiceWithMultipleGvlWithElderOrtb.sendAuctionRequest(bidRequest)

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

    def "PBS should leave the original request with ext.eids data for elder ortb when requireConsent is disabled and #enforcementRequirements.purpose have full consent"() {
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
        privacyPbsServiceWithMultipleGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest?.user?.ext?.eids == userEids

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
}
