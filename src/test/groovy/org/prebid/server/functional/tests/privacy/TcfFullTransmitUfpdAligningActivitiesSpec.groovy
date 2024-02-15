package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse
import org.prebid.server.functional.model.privacy.EnforcementRequirments
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.ActivityRule
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Condition
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.testcontainers.container.PrebidServerContainer
import org.prebid.server.functional.util.PBSUtils
import org.prebid.server.functional.util.privacy.TcfConsent
import org.prebid.server.functional.util.privacy.TcfUtils
import org.testcontainers.images.builder.Transferable
import spock.lang.Shared

import static com.iabtcf.v2.RestrictionType.REQUIRE_CONSENT
import static com.iabtcf.v2.RestrictionType.REQUIRE_LEGITIMATE_INTEREST
import static com.iabtcf.v2.RestrictionType.UNDEFINED
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
import static org.prebid.server.functional.model.config.PurposeEnforcement.FULL
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse.getDefaultVendorListResponse
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class TcfFullTransmitUfpdAligningActivitiesSpec extends PrivacyBaseSpec {

    @Shared
    static final int PURPOSES_ONLY_GVL_VERSION = PBSUtils.getRandomNumber(0, 4095)
    @Shared
    static final int LEG_INT_PURPOSES_ONLY_GVL_VERSION = PBSUtils.getRandomNumberWithExclusion(PURPOSES_ONLY_GVL_VERSION, 0, 4095)
    @Shared
    static final int LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION = PBSUtils.getRandomNumberWithExclusion([PURPOSES_ONLY_GVL_VERSION, LEG_INT_PURPOSES_ONLY_GVL_VERSION], 0, 4095)
    @Shared
    static final int PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION = PBSUtils.getRandomNumberWithExclusion([PURPOSES_ONLY_GVL_VERSION, LEG_INT_PURPOSES_ONLY_GVL_VERSION, LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION], 0, 4095)

    @Shared
    private static PrebidServerContainer privacyPbsContainerWithMultipleGvl

    @Shared
    private static PrebidServerContainer privacyPbsContainerWithMultipleGvlWithElderOrtb

    @Shared
    private static PrebidServerService privacyPbsServiceWithMultipleGvl

    @Shared
    private static PrebidServerService privacyPbsServiceWithMultipleGvlWithElderOrtb

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

        privacyPbsContainerWithMultipleGvlWithElderOrtb = new PrebidServerContainer(PBS_CONFIG + ["adapters.generic.ortb-version": "2.5"])
        privacyPbsContainerWithMultipleGvlWithElderOrtb.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithPurposesOnly), getVendorListPath(PURPOSES_ONLY_GVL_VERSION))
        privacyPbsContainerWithMultipleGvlWithElderOrtb.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithLegIntPurposes), getVendorListPath(LEG_INT_PURPOSES_ONLY_GVL_VERSION))
        privacyPbsContainerWithMultipleGvlWithElderOrtb.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithLegIntAndFlexiblePurposes), getVendorListPath(LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION))
        privacyPbsContainerWithMultipleGvlWithElderOrtb.withCopyToContainer(Transferable.of(prepareEncodeResponseBodyWithPurposesAndFlexiblePurposes), getVendorListPath(PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION))
        privacyPbsContainerWithMultipleGvlWithElderOrtb.start()
        privacyPbsServiceWithMultipleGvlWithElderOrtb = new PrebidServerService(privacyPbsContainerWithMultipleGvlWithElderOrtb)
    }

    def cleanupSpec() {
        privacyPbsContainerWithMultipleGvl.stop()
        privacyPbsContainerWithMultipleGvlWithElderOrtb.stop()
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled and #enforcementRequirments.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments << getLegalEnforcementRequirments(P4) + getCompanyEnforcementRequirments(P4)
    }

    def "PBS should leave the original request with ext.eids data for elder ortb when requireConsent is enabled and #enforcementRequirments.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.ext.eids == userEids

        where:
        enforcementRequirments << getLegalEnforcementRequirments(P4) + getCompanyEnforcementRequirments(P4)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and #enforcementRequirments.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getLegalEnforcementRequirments(P1) +
                getCompanyEnforcementRequirments(P1) +
                getLegalEnforcementRequirments(P2) +
                getCompanyEnforcementRequirments(P2) +
                getLegalEnforcementRequirments(P3) +
                getCompanyEnforcementRequirments(P3) +
                getLegalEnforcementRequirments(P5) +
                getCompanyEnforcementRequirments(P5) +
                getLegalEnforcementRequirments(P6) +
                getCompanyEnforcementRequirments(P6) +
                getLegalEnforcementRequirments(P7) +
                getCompanyEnforcementRequirments(P7) +
                getLegalEnforcementRequirments(P8) +
                getCompanyEnforcementRequirments(P8) +
                getLegalEnforcementRequirments(P9) +
                getCompanyEnforcementRequirments(P9) +
                getLegalEnforcementRequirments(P10) +
                getCompanyEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with ext.eids data for elder ortb when requireConsent is enabled and #enforcementRequirments.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getLegalEnforcementRequirments(P1) +
                getCompanyEnforcementRequirments(P1) +
                getLegalEnforcementRequirments(P2) +
                getCompanyEnforcementRequirments(P2) +
                getLegalEnforcementRequirments(P3) +
                getCompanyEnforcementRequirments(P3) +
                getLegalEnforcementRequirments(P5) +
                getCompanyEnforcementRequirments(P5) +
                getLegalEnforcementRequirments(P6) +
                getCompanyEnforcementRequirments(P6) +
                getLegalEnforcementRequirments(P7) +
                getCompanyEnforcementRequirments(P7) +
                getLegalEnforcementRequirments(P8) +
                getCompanyEnforcementRequirments(P8) +
                getLegalEnforcementRequirments(P9) +
                getCompanyEnforcementRequirments(P9) +
                getLegalEnforcementRequirments(P10) +
                getCompanyEnforcementRequirments(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled but bidder is excepted and #enforcementRequirments.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments << getLegalEnforcementRequirments(P2) +
                getLegalEnforcementRequirments(P3) +
                getLegalEnforcementRequirments(P4) +
                getLegalEnforcementRequirments(P5) +
                getLegalEnforcementRequirments(P6) +
                getLegalEnforcementRequirments(P7) +
                getLegalEnforcementRequirments(P8) +
                getLegalEnforcementRequirments(P9) +
                getLegalEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled, bidder is excepted and #enforcementRequirments.purpose have unsupported full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, userEids.source)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getLegalEnforcementRequirments(P1) +
                getCompanyEnforcementRequirments(P1) +
                getCompanyEnforcementRequirments(P2) +
                getCompanyEnforcementRequirments(P3) +
                getCompanyEnforcementRequirments(P4) +
                getCompanyEnforcementRequirments(P5) +
                getCompanyEnforcementRequirments(P6) +
                getCompanyEnforcementRequirments(P7) +
                getCompanyEnforcementRequirments(P8) +
                getCompanyEnforcementRequirments(P9) +
                getCompanyEnforcementRequirments(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is disabled and #enforcementRequirments.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.eids == userEids

        where:
        enforcementRequirments << getLegalEnforcementRequirments(P2) +
                getLegalEnforcementRequirments(P3) +
                getLegalEnforcementRequirments(P4) +
                getLegalEnforcementRequirments(P5) +
                getLegalEnforcementRequirments(P6) +
                getLegalEnforcementRequirments(P7) +
                getLegalEnforcementRequirments(P8) +
                getLegalEnforcementRequirments(P9) +
                getLegalEnforcementRequirments(P10)
    }

    def "PBS should leave the original request with ext.eids data for elder ortb when requireConsent is disabled and #enforcementRequirments.purpose have full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvlWithElderOrtb.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequest.user.ext.eids == userEids

        where:
        enforcementRequirments << getLegalEnforcementRequirments(P2) +
                getLegalEnforcementRequirments(P3) +
                getLegalEnforcementRequirments(P4) +
                getLegalEnforcementRequirments(P5) +
                getLegalEnforcementRequirments(P6) +
                getLegalEnforcementRequirments(P7) +
                getLegalEnforcementRequirments(P8) +
                getLegalEnforcementRequirments(P9) +
                getLegalEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is disabled and #enforcementRequirments.purpose have unsupported full consent"() {
        given: "Default Generic BidRequests with Eid field"
        def userEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, false)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def activity = Activity.getDefaultActivity([ActivityRule.getDefaultActivityRule(Condition.baseCondition, true)])
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, activity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        privacyPbsServiceWithMultipleGvl.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid field"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)
        assert !bidderRequest.user?.eids
        assert !bidderRequest.user?.ext?.eids

        where:
        enforcementRequirments << getLegalEnforcementRequirments(P1) +
                getCompanyEnforcementRequirments(P1) +
                getCompanyEnforcementRequirments(P2) +
                getCompanyEnforcementRequirments(P3) +
                getCompanyEnforcementRequirments(P5) +
                getCompanyEnforcementRequirments(P6) +
                getCompanyEnforcementRequirments(P7) +
                getCompanyEnforcementRequirments(P8) +
                getCompanyEnforcementRequirments(P9) +
                getCompanyEnforcementRequirments(P10)
    }

    private static List<EnforcementRequirments> getLegalEnforcementRequirments(Purpose purpose) {
        [new EnforcementRequirments(purpose: purpose,
                restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                vendorIdGvl: GENERIC_VENDOR_ID,
                enforcePurpose: FULL,
                purposeConsent: purpose,
                vendorConsentBitField: GENERIC_VENDOR_ID,
                vendorListVersion: PURPOSES_ONLY_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 purposesLITransparency: purpose,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposeConsent: purpose,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: purpose,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: purpose,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 purposeConsent: purpose,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: purpose,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposeConsent: purpose,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 purposeConsent: purpose,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: purpose,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION)
        ]
    }

    private static List<EnforcementRequirments> getCompanyEnforcementRequirments(Purpose purpose) {
        [new EnforcementRequirments(purpose: purpose,
                restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                vendorIdGvl: GENERIC_VENDOR_ID,
                enforcePurpose: NO,
                enforceVendor: false,
                vendorListVersion: PURPOSES_ONLY_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 enforcePurpose: FULL,
                 purposeConsent: purpose,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_ONLY_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_ONLY_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposeConsent: purpose,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 purposeConsent: purpose,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposeConsent: purpose,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 purposeConsent: purpose,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),
         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION),

         new EnforcementRequirments(purpose: purpose,
                 enforcePurpose: NO,
                 enforceVendor: false)]
    }

    static String getVendorListContent(boolean includePurposes, boolean includeLegIntPurposes, boolean includeFlexiblePurposes) {
        def purposeValues = TcfConsent.PurposeId.values().value
        def vendor = VendorListResponse.Vendor.getDefaultVendor(GENERIC_VENDOR_ID).tap {
            purposes = includePurposes ? purposeValues : []
            legIntPurposes = includeLegIntPurposes ? purposeValues : []
            flexiblePurposes = includeFlexiblePurposes ? purposeValues : []
            specialPurposes = []
            features = []
            specialFeatures = []
        }
        encode(defaultVendorListResponse.tap {
            it.tcfPolicyVersion = TCF_POLICY_V2.vendorListVersion
            it.vendors = [(GENERIC_VENDOR_ID): vendor]
        })
    }

    private static String getVendorListPath(Integer gvlVersion) {
        "/app/prebid-server/data/vendorlist-v${TCF_POLICY_V2.vendorListVersion}/${gvlVersion}.json"
    }
}
