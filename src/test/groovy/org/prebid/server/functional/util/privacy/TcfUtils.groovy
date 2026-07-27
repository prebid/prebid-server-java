package org.prebid.server.functional.util.privacy

import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.config.PurposeEid
import org.prebid.server.functional.model.privacy.EnforcementRequirement
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.config.PurposeEnforcement.NO
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId
import static org.prebid.server.functional.util.privacy.TcfConsent.TcfPolicyVersion.TCF_POLICY_V2

class TcfUtils {

    static Map<Purpose, PurposeConfig> getPurposeConfigsForPersonalizedAds(
            EnforcementRequirement requirement,
            boolean requireConsent = false,
            List<String> eidsExceptions = []) {

        def purposes = new EnumMap<Purpose, PurposeConfig>(Purpose)

        // Basic Ads required for any bidder call, should be present at least as company consent
        purposes[Purpose.P2] = buildPurposeConfig(new EnforcementRequirement(
                enforcePurpose: NO,
                enforceVendor: false
        ))

        def eid = buildPurposeEid(requireConsent, eidsExceptions)
        def config = buildPurposeConfig(requirement)
        if (requirement.purpose == Purpose.P4) {
            config.eid = eid
        } else {
            purposes[Purpose.P4] = new PurposeConfig(eid: eid)
        }
        purposes[requirement.purpose] = config

        purposes
    }

    private static PurposeConfig buildPurposeConfig(EnforcementRequirement requirement) {
        new PurposeConfig().tap {
            if (PBSUtils.randomBoolean) {
                it.enforcePurposeSnakeCase = requirement.enforcePurpose
                it.enforceVendorsSnakeCase = requirement.enforceVendor
                it.vendorExceptionsSnakeCase = requirement.vendorExceptions?.value
            } else {
                it.enforcePurpose = requirement.enforcePurpose
                it.enforceVendors = requirement.enforceVendor
                it.vendorExceptions = requirement.vendorExceptions?.value
            }
        }
    }

    private static PurposeEid buildPurposeEid(boolean requireConsent, List<String> exceptions) {
        new PurposeEid().tap {
            it.exceptions = exceptions

            if (PBSUtils.randomBoolean) {
                it.requireConsentKebabCase = requireConsent
            } else {
                it.requireConsent = requireConsent
            }
        }
    }


    static ConsentString getConsentString(EnforcementRequirement requirement) {
        def purposeConsent = requirement.enforcePurpose != NO ? requirement.purpose : null
        def purposeId = purposeConsent ? PurposeId.convertPurposeToPurposeId(purposeConsent) : null
        def currentPurposeId = PurposeId.convertPurposeToPurposeId(requirement.purpose)
        def builder = new TcfConsent.Builder()

        if (purposeConsent && !requirement.purposesLITransparency) {
            builder.setPurposesConsent(purposeId)
        }
        if (requirement.vendorConsentBitField) {
            builder.setVendorConsent(requirement.vendorConsentBitField)
        }
        if (requirement.purposesLITransparency) {
            builder.setPurposesLITransparency(currentPurposeId)
        }
        if (purposeConsent && requirement.restrictionType && requirement.vendorIdGvl) {
            builder.setPublisherRestrictionEntry(purposeId, requirement.restrictionType, requirement.vendorIdGvl)
        }
        if (requirement.vendorIdGvl) {
            builder.setVendorLegitimateInterest(requirement.vendorIdGvl)
        }
        if (requirement.disclosedVendorsId) {
            builder.setDisclosedVendors(requirement.disclosedVendorsId)
        }
        if (requirement.created) {
            builder.setCreateTime(requirement.created)
        }
        if (requirement.updated) {
            builder.setUpdatedTime(requirement.updated)
        }
        builder.setVendorListVersion(requirement.vendorListVersion ?: TCF_POLICY_V2.vendorListVersion)

        builder.build()
    }
}
