package org.prebid.server.functional.model.privacy

import com.iabtcf.v2.PublisherRestriction
import com.iabtcf.v2.RestrictionType
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeEnforcement

class EnforcementRequirments {

    Purpose purpose
    PurposeEnforcement enforcePurpose
    List<PublisherRestriction> publisherRestrictions
    Purpose purposeConsent
    Boolean enforceVendor
    Integer vendorConsentBitField
    Integer vendorLegitimateInterestBitfield
    List<BidderName> softVendorExceptions
    List<BidderName> vendorExceptions
    Purpose purposesLITransparency
    Purpose purposeGvl
    Purpose legIntPurposeGvl
    Purpose flexiblePurposeGvl
    BidderName vendorGvl
    Integer vendorIdGvl
    List<RestrictionType> restrictionType
}
