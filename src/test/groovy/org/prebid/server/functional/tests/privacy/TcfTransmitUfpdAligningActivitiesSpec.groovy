package org.prebid.server.functional.tests.privacy

import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.privacy.EnforcementRequirments
import org.prebid.server.functional.model.request.auction.Activity
import org.prebid.server.functional.model.request.auction.AllowActivities
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.util.privacy.TcfUtils

import static com.iabtcf.v2.RestrictionType.REQUIRE_CONSENT
import static com.iabtcf.v2.RestrictionType.REQUIRE_LEGITIMATE_INTEREST
import static com.iabtcf.v2.RestrictionType.UNDEFINED
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
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class TcfTransmitUfpdAligningActivitiesSpec extends PrivacyBaseSpec {

    private static final def DEFAULT_TCF_POLICY_VERSION = TCF_POLICY_V2

    def "PBS should leave the original request with eids data when requireConsent is enabled and personalized ADS purpose have basic consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

        where:
        enforcementRequirments << getBasicEnforcementRequirments(P4)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled and personalized ADS purpose have full consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        and: "Set vendor list response"
        vendorListResponse.setResponse(DEFAULT_TCF_POLICY_VERSION, TcfUtils.getGvlVendor(enforcementRequirments))

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

        where:
        enforcementRequirments << getFullEnforcementRequirments(P4)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and not personalized ADS purposes have basic consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        enforcementRequirments << getBasicEnforcementRequirments(P2) +
                getBasicPurposesLITEnforcementRequirments(P2) +
                getBasicEnforcementRequirments(P3) +
                getBasicEnforcementRequirments(P4) +
                getBasicEnforcementRequirments(P5) +
                getBasicEnforcementRequirments(P6) +
                getBasicEnforcementRequirments(P7) +
                getBasicEnforcementRequirments(P8) +
                getBasicEnforcementRequirments(P9) +
                getBasicEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and not personalized ADS purposes have full consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        and: "Set vendor list response"
        vendorListResponse.setResponse(DEFAULT_TCF_POLICY_VERSION, TcfUtils.getGvlVendor(enforcementRequirments))

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        enforcementRequirments << getFullEnforcementRequirments(P2) +
                getFullEnforcementRequirments(P3) +
                getFullEnforcementRequirments(P4) +
                getFullEnforcementRequirments(P5) +
                getFullEnforcementRequirments(P6) +
                getFullEnforcementRequirments(P7) +
                getFullEnforcementRequirments(P8) +
                getFullEnforcementRequirments(P9) +
                getFullEnforcementRequirments(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled but bidder is excepted and purposes have basic consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, [GENERIC])
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

        where:
        enforcementRequirments << getBasicEnforcementRequirments(P2) +
                getBasicPurposesLITEnforcementRequirments(P2) +
                getBasicEnforcementRequirments(P3) +
                getBasicEnforcementRequirments(P4) +
                getBasicEnforcementRequirments(P5) +
                getBasicEnforcementRequirments(P6) +
                getBasicEnforcementRequirments(P7) +
                getBasicEnforcementRequirments(P8) +
                getBasicEnforcementRequirments(P9) +
                getBasicEnforcementRequirments(P10)
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled but bidder is excepted and purposes have full consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, [GENERIC])
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        and: "Set vendor list response"
        vendorListResponse.setResponse(DEFAULT_TCF_POLICY_VERSION, TcfUtils.getGvlVendor(enforcementRequirments))

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

        where:
        enforcementRequirments << getFullEnforcementRequirments(P2) +
                getFullEnforcementRequirments(P3) +
                getFullEnforcementRequirments(P4) +
                getFullEnforcementRequirments(P5) +
                getFullEnforcementRequirments(P6) +
                getFullEnforcementRequirments(P7) +
                getFullEnforcementRequirments(P8) +
                getFullEnforcementRequirments(P9) +
                getFullEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled, bidder is excepted and purposes have unsupported basic consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, [GENERIC])
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

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

    def "PBS should remove the original request with eids data when requireConsent is enabled, bidder is excepted and purposes have unsupported full consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def tcfConsent = TcfUtils.getConsentString(enforcementRequirments)
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true, [GENERIC])
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        and: "Set vendor list response"
        vendorListResponse.setResponse(DEFAULT_TCF_POLICY_VERSION, TcfUtils.getGvlVendor(enforcementRequirments))

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        enforcementRequirments << getFullEnforcementRequirments(P1)
    }

    def "PBS should leave the original request with eids data when requireConsent is disabled and purposes have basic consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

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

    def "PBS should leave the original request with eids data when requireConsent is disabled and purposes have full consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        and: "Set vendor list response"
        vendorListResponse.setResponse(DEFAULT_TCF_POLICY_VERSION, TcfUtils.getGvlVendor(enforcementRequirments))

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

        where:
        enforcementRequirments << getFullEnforcementRequirments(P2) +
                getFullEnforcementRequirments(P3) +
                getFullEnforcementRequirments(P4) +
                getFullEnforcementRequirments(P5) +
                getFullEnforcementRequirments(P6) +
                getFullEnforcementRequirments(P7) +
                getFullEnforcementRequirments(P8) +
                getFullEnforcementRequirments(P9) +
                getFullEnforcementRequirments(P10)
    }

    def "PBS should remove the original request with eids data when requireConsent is disabled and device access purpose have unsupported basic consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

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

    def "PBS should remove the original request with eids data when requireConsent is disabled and device access purpose have unsupported full consent"() {
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
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig).tap {
            config.privacy.allowActivities = AllowActivities.getDefaultAllowActivities(TRANSMIT_UFPD, Activity.defaultActivity)
        }
        accountDao.save(account)

        and: "Set vendor list response"
        vendorListResponse.setResponse(DEFAULT_TCF_POLICY_VERSION, TcfUtils.getGvlVendor(enforcementRequirments))

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        enforcementRequirments << getFullEnforcementRequirments(P1)
    }

    private static List<EnforcementRequirments> getBasicEnforcementRequirments(Purpose purpose) {
        [new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: purpose, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: purpose, enforceVendor: false),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: NO, enforceVendor: false),
         new EnforcementRequirments(purpose: purpose, vendorExceptions: [GENERIC]),
         new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: purpose, softVendorExceptions: [GENERIC]),
         new EnforcementRequirments(purpose: purpose, enforcePurpose: NO, softVendorExceptions: [GENERIC])]
    }

    private static List<EnforcementRequirments> getBasicPurposesLITEnforcementRequirments(Purpose purpose) {
        [new EnforcementRequirments(purpose: purpose, enforcePurpose: BASIC, purposesLITransparency: purpose)]
    }

    private static List<EnforcementRequirments> getFullEnforcementRequirments(Purpose purpose) {
        [new EnforcementRequirments(purpose: purpose,
                purposeGvl: purpose,
                vendorIdGvl: GENERIC_VENDOR_ID,
                flexiblePurposeGvl: null,
                restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                enforcePurpose: NO,
                enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: null,
                 restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                 purposeConsent: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: null,
                 restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: null,
                 restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                 purposeConsent: purpose,
                 vendorConsentBitField: GENERIC_VENDOR_ID),

         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: null,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 enforcePurpose: NO, enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: null,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 purposesLITransparency: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: null,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: null,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),

         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 purposeConsent: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 purposeConsent: purpose,
                 vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),

         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),

         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 purposeConsent: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 legIntPurposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 purposeConsent: purpose,
                 vendorConsentBitField: GENERIC_VENDOR_ID),

         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 purposeConsent: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 purposeConsent: purpose,
                 vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),

         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 purposeConsent: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 purposeConsent: purpose,
                 vendorConsentBitField: GENERIC_VENDOR_ID),

         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: purpose,
                 enforceVendor: false),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),
         new EnforcementRequirments(purpose: purpose,
                 purposeGvl: purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 flexiblePurposeGvl: purpose,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: purpose,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),

         new EnforcementRequirments(purpose: purpose,
                 enforcePurpose: NO,
                 enforceVendor: false)]
    }
}
