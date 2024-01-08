package org.prebid.server.functional.util.privacy

import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeConfig
import org.prebid.server.functional.model.config.PurposeEid
import org.prebid.server.functional.model.mock.services.vendorlist.VendorListResponse
import org.prebid.server.functional.model.privacy.EnforcementRequirments

import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID
import static org.prebid.server.functional.util.privacy.TcfConsent.GENERIC_VENDOR_ID

class TcfUtils {

    static Map<Purpose, PurposeConfig> getPurposeConfigsForPersonalizedAds(EnforcementRequirments enforcementRequirments, boolean requireConsent = false, List<BidderName> exceptions = []) {
        def purpose = enforcementRequirments.purposeConsent != null ? enforcementRequirments.purposeConsent : enforcementRequirments.purpose
        def purposeConfig = new PurposeConfig(
                enforcePurpose: enforcementRequirments.enforcePurpose,
                enforceVendors: enforcementRequirments.enforceVendor,
                softVendorExceptions: enforcementRequirments.softVendorExceptions.value,
                vendorExceptions: enforcementRequirments.vendorExceptions.value
        )
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
        def purpose = enforcementRequirments.purposeConsent != null ? enforcementRequirments.purposeConsent : enforcementRequirments.purpose
        new TcfConsent.Builder()
                .setPurposesConsent(TcfConsent.PurposeId.convertPurposeToPurposeId(enforcementRequirments.purposeConsent))
                .setVendorConsent(enforcementRequirments.getVendorConsentBitField())
                .setPurposesLITransparency(TcfConsent.PurposeId.convertPurposeToPurposeId(enforcementRequirments.getPurposesLITransparency()))
                .setPublisherRestrictionEntry(TcfConsent.PurposeId.convertPurposeToPurposeId(purpose), enforcementRequirments.restrictionType, enforcementRequirments.vendorIdGvl)
                .setVendorLegitimateInterest(enforcementRequirments.vendorIdGvl)
                .build()
    }

    static Map<Integer, VendorListResponse.Vendor> getGvlVendor(EnforcementRequirments enforcementRequirments) {
        def vendor = VendorListResponse.Vendor.getDefaultVendor(enforcementRequirments.vendorIdGvl).tap {
            it.purposes = [TcfConsent.PurposeId.convertPurposeToPurposeId(enforcementRequirments.purposeGvl).value]
            it.legIntPurposes = [TcfConsent.PurposeId.convertPurposeToPurposeId(enforcementRequirments.legIntPurposeGvl).value]
            it.flexiblePurposes = [TcfConsent.PurposeId.convertPurposeToPurposeId(enforcementRequirments.flexiblePurposeGvl).value]
        }
        [(enforcementRequirments.vendorIdGvl): vendor]
    }
}
