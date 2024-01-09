package org.prebid.server.functional.util.privacy

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.config.PurposeEid
import org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse
import org.prebid.server.functional.model.privacy.EnforcementRequirments

import static org.prebid.server.functional.util.privacy.TcfConsent.Builder
import static org.prebid.server.functional.util.privacy.TcfConsent.PurposeId

class TcfUtils {

    static Map<Purpose, PurposeConfig> getPurposeConfigsForPersonalizedAds(EnforcementRequirments enforcementRequirments, boolean requireConsent = false, List<BidderName> exceptions = []) {
        def purpose = enforcementRequirments.purposeConsent ?: enforcementRequirments.purpose
        def purposeConfig = new PurposeConfig(enforcePurpose: enforcementRequirments.enforcePurpose,
                enforceVendors: enforcementRequirments.enforceVendor,
                softVendorExceptions: enforcementRequirments?.softVendorExceptions?.value,
                vendorExceptions: enforcementRequirments?.vendorExceptions?.value)
        def purposeEid = new PurposeEid(requireConsent: requireConsent, exceptions: exceptions.value)
        if (purpose == Purpose.P4) {
            purposeConfig.eid = purposeEid
            [(Purpose.P4): purposeConfig]
        } else {
            [(purpose)   : purposeConfig,
             (Purpose.P4): new PurposeConfig(eid: purposeEid)]
        }
    }

    static ConsentString getConsentString(EnforcementRequirments enforcementRequirments) {
        def purposeConsent = enforcementRequirments.purposeConsent
        def purpose = enforcementRequirments.purposeConsent ?: enforcementRequirments.purpose
        def vendorConsentBitField = enforcementRequirments.getVendorConsentBitField()
        def purposesLITransparency = enforcementRequirments.getPurposesLITransparency()
        def restrictionType = enforcementRequirments.restrictionType
        def vendorIdGvl = enforcementRequirments.vendorIdGvl
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
        return builder.build()
    }

    static Map<Integer, VendorListResponse.Vendor> getGvlVendor(EnforcementRequirments enforcementRequirments) {
        def vendor = VendorListResponse.Vendor.getDefaultVendor(enforcementRequirments?.vendorIdGvl).tap {
            purposes = enforcementRequirments?.purposeGvl ? [PurposeId.convertPurposeToPurposeId(enforcementRequirments.purposeGvl).value] : []
            legIntPurposes = enforcementRequirments?.legIntPurposeGvl ? [PurposeId.convertPurposeToPurposeId(enforcementRequirments.legIntPurposeGvl).value] : []
            flexiblePurposes = enforcementRequirments?.flexiblePurposeGvl ? [PurposeId.convertPurposeToPurposeId(enforcementRequirments.flexiblePurposeGvl).value] : []
        }
        [(enforcementRequirments.vendorIdGvl): vendor]
    }
}
