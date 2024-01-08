package org.prebid.server.functional.tests.privacy

import com.iabtcf.v2.RestrictionType
import org.prebid.server.functional.model.config.AccountGdprConfig
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.config.PurposeEid
import org.prebid.server.functional.model.privacy.EnforcementRequirments
import org.prebid.server.functional.model.request.auction.Eid
import org.prebid.server.functional.util.privacy.TcfUtils
import spock.lang.IgnoreRest

import static com.iabtcf.v2.RestrictionType.REQUIRE_CONSENT
import static com.iabtcf.v2.RestrictionType.REQUIRE_LEGITIMATE_INTEREST
import static com.iabtcf.v2.RestrictionType.UNDEFINED
import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.Purpose.P4
import static org.prebid.server.functional.model.config.PurposeEnforcement.BASIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse.Vendor
import static org.prebid.server.functional.util.privacy.TcfConsent.Builder
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId.PERSONALIZED_ADS
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class TcfTransmitEidsActivitiesSpec extends PrivacyBaseSpec {

    private static final def DEFAULT_TCF_POLICY_VERSION = TCF_POLICY_V2

    def "PBS should leave the original request with eids data when requireConsent is enabled and P4 have full consent"() {
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
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
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
        enforcementRequirments << [
                new EnforcementRequirments(purpose: P4, purposeGvl: P4, vendorIdGvl: GENERIC_VENDOR_ID, flexiblePurposeGvl: null, restrictionType: [REQUIRE_CONSENT, UNDEFINED], enforcePurpose: null, enforceVendor: null),
                new EnforcementRequirments(purpose: P4, purposeGvl: P4, vendorIdGvl: GENERIC_VENDOR_ID, flexiblePurposeGvl: null, restrictionType: [REQUIRE_CONSENT, UNDEFINED], purposeConsent: P4, enforceVendor: null),
                new EnforcementRequirments(purpose: P4, purposeGvl: P4, vendorIdGvl: GENERIC_VENDOR_ID, flexiblePurposeGvl: null, restrictionType: [REQUIRE_CONSENT, UNDEFINED], enforcePurpose: null, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: P4, purposeGvl: P4, vendorIdGvl: GENERIC_VENDOR_ID, flexiblePurposeGvl: null, restrictionType: [REQUIRE_CONSENT, UNDEFINED], purposeConsent: P4, vendorConsentBitField: GENERIC_VENDOR_ID),

                new EnforcementRequirments(purpose: P4, legIntPurposeGvl: P4, vendorIdGvl: GENERIC_VENDOR_ID, flexiblePurposeGvl: null, restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED], enforcePurpose: null, enforceVendor: null),
                new EnforcementRequirments(purpose: P4, legIntPurposeGvl: P4, vendorIdGvl: GENERIC_VENDOR_ID, flexiblePurposeGvl: null, restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED], purposesLITransparency: P4, enforceVendor: null),
                new EnforcementRequirments(purpose: P4, legIntPurposeGvl: P4, vendorIdGvl: GENERIC_VENDOR_ID, flexiblePurposeGvl: null, restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED], enforcePurpose: null, vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: P4, legIntPurposeGvl: P4, vendorIdGvl: GENERIC_VENDOR_ID, flexiblePurposeGvl: null, restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED], purposesLITransparency: P4, vendorLegitimateInterestBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, softVendorExceptions: [GENERIC])
        ]
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled and P4 have basic consent"() {
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
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

        where:
        enforcementRequirments << [
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, softVendorExceptions: [GENERIC])
        ]
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled and #purposeVersion have basic consent"() {
        given: "Default Generic BidRequests with EID fields"
        def userEids = [Eid.defaultEid]
        def userExtEids = [Eid.defaultEid]
        def bidRequest = getGdprBidRequest(tcfConsent).tap {
            it.user.eids = userEids
            it.user.ext.eids = userExtEids
        }

        and: "Save account config with requireConsent into DB"
        def purposes = TcfUtils.getPurposeConfigsForPersonalizedAds(enforcementRequirments, true)
        def accountGdprConfig = new AccountGdprConfig(purposes: purposes)
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        enforcementRequirments << [
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P2, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P2, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: BASIC, purposesLITransparency: Purpose.P2),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P2, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P2, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P3, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P3, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: BASIC, purposesLITransparency: Purpose.P3),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P3, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P3, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P5, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P5, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P5, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P5, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P6, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P6, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P6, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P6, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P7, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P7, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P7, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P7, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P8, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P8, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P8, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P8, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P9, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P9, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P9, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P9, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P10, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P10, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P10, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P10, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: NO, softVendorExceptions: [GENERIC])
        ]
    }

    def "PBS should leave the original request with eids data when requireConsent is enabled but bidder is excepted and #purposeVersion have basic consent"() {
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
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        enforcementRequirments << [
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P2, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P2, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: BASIC, purposesLITransparency: Purpose.P2),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P2, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P2, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P3, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P3, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: BASIC, purposesLITransparency: Purpose.P3),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P3, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P3, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P5, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P5, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P5, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P5, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P6, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P6, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P6, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P6, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P7, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P7, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P7, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P7, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P8, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P8, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P8, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P8, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P9, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P9, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P9, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P9, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P10, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P10, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P10, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P10, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: NO, softVendorExceptions: [GENERIC])
        ]
    }

    def "PBS should remove the original request with eids data when requireConsent is enabled, bidder is excepted and #purposeVersion have unsupported basic consent"() {
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
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        enforcementRequirments << [
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: BASIC, purposesLITransparency: Purpose.P10),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: BASIC, purposesLITransparency: Purpose.P9),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: BASIC, purposesLITransparency: Purpose.P8),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: BASIC, purposesLITransparency: Purpose.P7),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: BASIC, purposesLITransparency: Purpose.P6),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: BASIC, purposesLITransparency: Purpose.P5),
                new EnforcementRequirments(purpose: P4, enforcePurpose: BASIC, purposesLITransparency: P4),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: BASIC, purposesLITransparency: Purpose.P3),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P1, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P1, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P1, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P1, enforcePurpose: BASIC, purposesLITransparency: Purpose.P1),
                new EnforcementRequirments(purpose: Purpose.P1, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P1, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P1, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P1, enforcePurpose: NO, softVendorExceptions: [GENERIC])
        ]
    }

    def "PBS should leave the original request with eids data when requireConsent is disabled and #purposeVersion have basic consent"() {
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
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request should have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert bidderRequest.user.eids == userEids
        assert bidderRequest.user.ext.eids == userExtEids

        where:
        enforcementRequirments << [
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P2, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P2, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: BASIC, purposesLITransparency: Purpose.P2),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P2, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P2, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P2, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P3, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P3, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: BASIC, purposesLITransparency: Purpose.P3),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P3, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P3, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: P4, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: P4, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: P4, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P5, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P5, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P5, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P5, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P6, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P6, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P6, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P6, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P7, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P7, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P7, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P7, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P8, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P8, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P8, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P8, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P9, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P9, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P9, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P9, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: NO, softVendorExceptions: [GENERIC]),

                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P10, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P10, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P10, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P10, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: NO, softVendorExceptions: [GENERIC])
        ]
    }

    def "PBS should remove the original request with eids data when requireConsent is disabled and purpose #purposeVersion have unsupported basic consent"() {
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
        def account = getAccountWithGdpr(bidRequest.accountId, accountGdprConfig)
        accountDao.save(account)

        when: "PBS processes auction requests"
        activityPbsService.sendAuctionRequest(bidRequest)

        then: "Generic bidder request shouldn't have data in Eid fields"
        def bidderRequest = bidder.getBidderRequest(bidRequest.id)

        assert !bidderRequest.user.eids
        assert !bidderRequest.user.ext.eids

        where:
        enforcementRequirments << [
                new EnforcementRequirments(purpose: Purpose.P10, enforcePurpose: BASIC, purposesLITransparency: Purpose.P10),
                new EnforcementRequirments(purpose: Purpose.P9, enforcePurpose: BASIC, purposesLITransparency: Purpose.P9),
                new EnforcementRequirments(purpose: Purpose.P8, enforcePurpose: BASIC, purposesLITransparency: Purpose.P8),
                new EnforcementRequirments(purpose: Purpose.P7, enforcePurpose: BASIC, purposesLITransparency: Purpose.P7),
                new EnforcementRequirments(purpose: Purpose.P6, enforcePurpose: BASIC, purposesLITransparency: Purpose.P6),
                new EnforcementRequirments(purpose: Purpose.P5, enforcePurpose: BASIC, purposesLITransparency: Purpose.P5),
                new EnforcementRequirments(purpose: P4, enforcePurpose: BASIC, purposesLITransparency: P4),
                new EnforcementRequirments(purpose: Purpose.P3, enforcePurpose: BASIC, purposesLITransparency: Purpose.P3),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P1, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P1, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P1, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID),
                new EnforcementRequirments(purpose: Purpose.P1, enforcePurpose: BASIC, purposesLITransparency: Purpose.P1),
                new EnforcementRequirments(purpose: Purpose.P1, enforcePurpose: NO, enforceVendor: false),
                new EnforcementRequirments(purpose: Purpose.P1, vendorExceptions: [GENERIC]),
                new EnforcementRequirments(enforcePurpose: BASIC, purposeConsent: Purpose.P1, softVendorExceptions: [GENERIC]),
                new EnforcementRequirments(purpose: Purpose.P1, enforcePurpose: NO, softVendorExceptions: [GENERIC])
        ]
    }
}
