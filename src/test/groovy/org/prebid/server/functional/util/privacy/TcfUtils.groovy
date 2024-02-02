package org.prebid.server.functional.util.privacy

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.config.PurposeEid
import org.prebid.server.functional.model.config.PurposeEnforcement
import org.prebid.server.functional.model.privacy.EnforcementRequirments

import static org.prebid.server.functional.util.privacy.TcfConsent.Builder
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class TcfUtils {

    static Map<Purpose, PurposeConfig> getPurposeConfigsForPersonalizedAds(EnforcementRequirments enforcementRequirments, boolean requireConsent = false, List<String> eidsExceptions = []) {
        def purpose = enforcementRequirments.purposeConsent ?: enforcementRequirments.purpose
        def purposeConfig = new PurposeConfig(enforcePurpose: enforcementRequirments.enforcePurpose,
                enforceVendors: enforcementRequirments?.enforceVendor,
                softVendorExceptions: enforcementRequirments?.softVendorExceptions,
                vendorExceptions: enforcementRequirments?.vendorExceptions?.value)
        def purposeEid = new PurposeEid(requireConsent: requireConsent, exceptions: eidsExceptions)
        Map<Purpose, PurposeConfig> purposes = [:]
//        purposes[Purpose.P2] = new PurposeConfig(enforcePurpose: PurposeEnforcement.BASIC, enforceVendors: false)
        if (purpose == Purpose.P4) {
            purposeConfig.eid = purposeEid
            purposes[Purpose.P4] = purposeConfig
        } else {
            purposes[purpose] = purposeConfig
            purposes[Purpose.P4] = new PurposeConfig(eid: purposeEid)
        }
        purposes
    }

    static ConsentString getConsentString(EnforcementRequirments enforcementRequirments) {
        def purposeConsent = enforcementRequirments.purposeConsent
        def purpose = enforcementRequirments.purposeConsent ?: enforcementRequirments.purpose
        def vendorConsentBitField = enforcementRequirments.getVendorConsentBitField()
        def purposesLITransparency = enforcementRequirments.getPurposesLITransparency()
        def restrictionType = enforcementRequirments.restrictionType
        def vendorIdGvl = enforcementRequirments.vendorIdGvl
        def builder = new Builder()
//        builder.setPurposesConsent(PurposeId.convertPurposeToPurposeId(Purpose.P2))
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
        builder.setVendorListVersion(enforcementRequirments.vendorListVersion ?: TCF_POLICY_V2.vendorListVersion)
        return builder.build()
    }
}
