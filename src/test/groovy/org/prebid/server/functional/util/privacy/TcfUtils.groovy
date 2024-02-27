package org.prebid.server.functional.util.privacy

import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.config.PurposeEid
import org.prebid.server.functional.model.privacy.EnforcementRequirement

import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.util.privacy.TcfConsent.Builder
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class TcfUtils {

    static Map<Purpose, PurposeConfig> getPurposeConfigsForPersonalizedAds(EnforcementRequirement enforcementRequirements, boolean requireConsent = false, List<String> eidsExceptions = []) {
        def purpose = enforcementRequirements.purposeConsent ?: enforcementRequirements.purpose
        // Basic Ads required for any bidder call, should be present at least as company consent
        def purposes = [(Purpose.P2): new PurposeConfig(enforcePurpose: NO, enforceVendors: false)]
        def purposeConfig = new PurposeConfig(enforcePurpose: enforcementRequirements.enforcePurpose,
                enforceVendors: enforcementRequirements?.enforceVendor,
                vendorExceptions: enforcementRequirements?.vendorExceptions?.value)
        def purposeEid = new PurposeEid(requireConsent: requireConsent, exceptions: eidsExceptions)
        if (purpose == Purpose.P4) {
            purposeConfig.eid = purposeEid
            purposes[Purpose.P4] = purposeConfig
        } else {
            purposes[purpose] = purposeConfig
            purposes[Purpose.P4] = new PurposeConfig(eid: purposeEid)
        }
        purposes
    }

    static ConsentString getConsentString(EnforcementRequirement enforcementRequirements) {
        def purposeConsent = enforcementRequirements.purposeConsent
        def purpose = enforcementRequirements.purposeConsent ?: enforcementRequirements.purpose
        def vendorConsentBitField = enforcementRequirements.getVendorConsentBitField()
        def purposesLITransparency = enforcementRequirements.getPurposesLITransparency()
        def restrictionType = enforcementRequirements.restrictionType
        def vendorIdGvl = enforcementRequirements.vendorIdGvl
        def builder = new Builder()
        if (purposeConsent != null) {
            builder.setPurposesConsent(PurposeId.convertPurposeToPurposeId(purposeConsent))
        }
        if (vendorConsentBitField != null) {
            builder.setVendorConsent(vendorConsentBitField)
        }
        if (purposesLITransparency != null) {
            builder.setPurposesLITransparency(PurposeId.convertPurposeToPurposeId(purposesLITransparency))
        }
        if (purpose != null && restrictionType != null && vendorIdGvl != null) {
            builder.setPublisherRestrictionEntry(PurposeId.convertPurposeToPurposeId(purpose), restrictionType, vendorIdGvl)
        }
        if (vendorIdGvl != null) {
            builder.setVendorLegitimateInterest(vendorIdGvl)
        }
        builder.setVendorListVersion(enforcementRequirements.vendorListVersion ?: TCF_POLICY_V2.vendorListVersion)
        return builder.build()
    }
}
