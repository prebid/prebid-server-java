package org.prebid.server.functional.tests.privacy.tcf

import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.privacy.EnforcementRequirement
import org.prebid.server.functional.service.PrebidServerService
import org.prebid.server.functional.tests.privacy.PrivacyBaseSpec
import org.testcontainers.images.builder.Transferable

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.BASIC
import static org.prebid.server.functional.model.config.PurposeEnforcement.FULL
import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.RestrictionType.REQUIRE_CONSENT
import static org.prebid.server.functional.util.privacy.TcfConsent.RestrictionType.REQUIRE_LEGITIMATE_INTEREST
import static org.prebid.server.functional.util.privacy.TcfConsent.RestrictionType.UNDEFINED

class TcfBaseSpec extends PrivacyBaseSpec {

    private static final Map<String, String> PBS_CONFIG = SETTING_CONFIG + GENERIC_VENDOR_CONFIG + GENERIC_CONFIG + ["gdpr.vendorlist.v2.http-endpoint-template": null,
                                                                                                                     "gdpr.vendorlist.v3.http-endpoint-template": null]
    private static String prepareEncodeResponseBodyWithPurposesOnly = getVendorListContent(true, false, false)
    private static String prepareEncodeResponseBodyWithLegIntPurposes = getVendorListContent(false, true, false)
    private static String prepareEncodeResponseBodyWithLegIntAndFlexiblePurposes = getVendorListContent(false, true, true)
    private static String prepareEncodeResponseBodyWithPurposesAndFlexiblePurposes = getVendorListContent(true, false, true)
    private static Map<String, Transferable> GLV_LISTS_FILES = [(getVendorListPath(PURPOSES_ONLY_GVL_VERSION) )               : Transferable.of(prepareEncodeResponseBodyWithPurposesOnly),
                                                                (getVendorListPath(LEG_INT_PURPOSES_ONLY_GVL_VERSION) )       : Transferable.of(prepareEncodeResponseBodyWithLegIntPurposes),
                                                                (getVendorListPath(LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION)): Transferable.of(prepareEncodeResponseBodyWithLegIntAndFlexiblePurposes),
                                                                (getVendorListPath(PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION)): Transferable.of(prepareEncodeResponseBodyWithPurposesAndFlexiblePurposes)
    ]

    protected static PrebidServerService privacyPbsServiceWithMultipleGvl
    protected static PrebidServerService activityPbsServiceExcludeGvl

    def setupSpec() {
        activityPbsServiceExcludeGvl = pbsServiceFactory.getService(PBS_CONFIG)
        privacyPbsServiceWithMultipleGvl = pbsServiceFactory.getService(GENERAL_PRIVACY_CONFIG, GLV_LISTS_FILES)
    }

    protected static List<EnforcementRequirement> getBasicTcfCompanyBasedEnforcementRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, enforceVendor: false, disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: purpose, enforcePurpose: NO, enforceVendor: false, disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: purpose, enforcePurpose: NO, enforceVendor: true, vendorConsentBitField: GENERIC_VENDOR_ID, disclosedVendorsId: [GENERIC_VENDOR_ID])
        ]
    }

    protected static List<EnforcementRequirement> getBasicTcfLegalBasedEnforcementRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose,
                enforcePurpose: BASIC,
                enforceVendor: true,
                vendorConsentBitField: GENERIC_VENDOR_ID,
                disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: purpose, vendorExceptions: [GENERIC])
        ]
    }

    protected static List<EnforcementRequirement> getBasicTcfCompanySoftVendorExceptionsRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, vendorExceptions: [GENERIC]),
         new EnforcementRequirement(purpose: purpose, enforcePurpose: NO, vendorExceptions: [GENERIC])]
    }

    protected static List<EnforcementRequirement> getBasicTcfLegalPurposesLITEnforcementRequirements(Purpose purpose) {
        [new EnforcementRequirement(purpose: purpose, enforcePurpose: BASIC, purposesLITransparency: true, disclosedVendorsId: [GENERIC_VENDOR_ID])]
    }

    protected static List<EnforcementRequirement> getFullTcfLegalEnforcementRequirements(Purpose purpose, boolean isPurposeExcludedAndListRandom = false) {
        [new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                vendorIdGvl: GENERIC_VENDOR_ID,
                enforcePurpose: FULL,
                vendorConsentBitField: GENERIC_VENDOR_ID,
                vendorListVersion: PURPOSES_ONLY_GVL_VERSION,
                disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),


         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),


         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
        ]
    }

    protected static List<EnforcementRequirement> getFullTcfLegalEnforcementRequirementsRandomlyWithExcludePurpose(Purpose purpose) {
        getFullTcfLegalEnforcementRequirements(purpose, true)
    }

    protected static List<EnforcementRequirement> getFullTcfLegalLegitimateInterestsRequirements(Purpose purpose, boolean isPurposeExcludedAndListRandom = false) {
        [new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                vendorIdGvl: GENERIC_VENDOR_ID,
                restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                purposesLITransparency: true,
                vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION,
                disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: true,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: true,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 purposesLITransparency: true,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: true,
                 vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 purposesLITransparency: true,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID])
        ]
    }

    protected static List<EnforcementRequirement> getFullTcfCompanyEnforcementRequirements(Purpose purpose, boolean isPurposeExcludedAndListRandom = false) {
        [new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                restrictionType: [REQUIRE_CONSENT, UNDEFINED],
                vendorIdGvl: GENERIC_VENDOR_ID,
                enforcePurpose: NO,
                enforceVendor: false,
                vendorListVersion: PURPOSES_ONLY_GVL_VERSION,
                disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 enforcePurpose: FULL,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_ONLY_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 restrictionType: [REQUIRE_CONSENT],
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_ONLY_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),


         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforceVendor: false,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [UNDEFINED],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),
         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_CONSENT],
                 enforcePurpose: NO,
                 vendorConsentBitField: GENERIC_VENDOR_ID,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 vendorIdGvl: GENERIC_VENDOR_ID,
                 restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                 enforcePurpose: NO,
                 enforceVendor: false,
                 vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                 disclosedVendorsId: [GENERIC_VENDOR_ID]),

         new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                 enforcePurpose: NO,
                 enforceVendor: false,
                 disclosedVendorsId: [GENERIC_VENDOR_ID])]
    }

    protected static List<EnforcementRequirement> getFullTcfCompanyEnforcementRequirementsRandomlyWithExcludePurpose(Purpose purpose) {
        getFullTcfCompanyEnforcementRequirements(purpose, true)
    }

    protected static List<EnforcementRequirement> getFullTcfCompanyLegitimateInterestsRequirements(Purpose purpose, boolean isPurposeExcludedAndListRandom = false) {
        [
                new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                        vendorIdGvl: GENERIC_VENDOR_ID,
                        restrictionType: [REQUIRE_LEGITIMATE_INTEREST, UNDEFINED],
                        enforcePurpose: NO,
                        vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                        vendorListVersion: LEG_INT_PURPOSES_ONLY_GVL_VERSION,
                        disclosedVendorsId: [GENERIC_VENDOR_ID]),
                new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                        vendorIdGvl: GENERIC_VENDOR_ID,
                        restrictionType: [UNDEFINED],
                        enforcePurpose: NO,
                        vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                        vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                        disclosedVendorsId: [GENERIC_VENDOR_ID]),

                new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                        vendorIdGvl: GENERIC_VENDOR_ID,
                        restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                        enforcePurpose: NO,
                        vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                        vendorListVersion: LEG_INT_AND_FLEXIBLE_PURPOSES_GVL_VERSION,
                        disclosedVendorsId: [GENERIC_VENDOR_ID]),

                new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                        vendorIdGvl: GENERIC_VENDOR_ID,
                        restrictionType: [UNDEFINED],
                        enforcePurpose: NO,
                        vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                        vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                        disclosedVendorsId: [GENERIC_VENDOR_ID]),

                new EnforcementRequirement(purpose: isPurposeExcludedAndListRandom ? getRandomPurposeWithExclusion(purpose) : purpose,
                        vendorIdGvl: GENERIC_VENDOR_ID,
                        restrictionType: [REQUIRE_LEGITIMATE_INTEREST],
                        enforcePurpose: NO,
                        vendorLegitimateInterestBitField: GENERIC_VENDOR_ID,
                        vendorListVersion: PURPOSES_AND_LEG_INT_PURPOSES_GVL_VERSION,
                        disclosedVendorsId: [GENERIC_VENDOR_ID])
        ]
    }

    private static Purpose getRandomPurposeWithExclusion(Purpose excludeFromRandom) {
        def availablePurposes = Purpose.values().toList() - excludeFromRandom
        availablePurposes.shuffled().first()
    }
}
