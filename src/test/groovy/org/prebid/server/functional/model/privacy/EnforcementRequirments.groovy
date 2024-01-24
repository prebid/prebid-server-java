package org.prebid.server.functional.model.privacy

import com.iabtcf.v2.PublisherRestriction
import com.iabtcf.v2.RestrictionType
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeEnforcement

class EnforcementRequirments {

    Purpose purpose
    PurposeEnforcement enforcePurpose
    Purpose purposeConsent
    Boolean enforceVendor
    Integer vendorConsentBitField
    Integer vendorLegitimateInterestBitField
    List<BidderName> vendorExceptions
    Purpose purposesLITransparency
    List<RestrictionType> restrictionType
    Integer vendorIdGvl
    Integer vendorListVersion
}
