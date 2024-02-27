package org.prebid.server.functional.model.privacy

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeEnforcement
import org.prebid.server.functional.util.privacy.TcfConsent

@ToString(includeNames = true, ignoreNulls = true)
class EnforcementRequirements {

    Purpose purpose
    PurposeEnforcement enforcePurpose
    Purpose purposeConsent
    Boolean enforceVendor
    Integer vendorConsentBitField
    Integer vendorLegitimateInterestBitField
    List<BidderName> vendorExceptions
    Purpose purposesLITransparency
    List<TcfConsent.RestrictionType> restrictionType
    Integer vendorIdGvl
    Integer vendorListVersion
}
