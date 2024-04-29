package org.prebid.server.functional.model.privacy

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeEnforcement
import org.prebid.server.functional.util.privacy.TcfConsent

@ToString(includeNames = true, ignoreNulls = true)
class EnforcementRequirement {

    Purpose purpose
    PurposeEnforcement enforcePurpose
    Boolean enforceVendor
    Integer vendorConsentBitField
    Integer vendorLegitimateInterestBitField
    List<BidderName> vendorExceptions
    boolean purposesLITransparency
    List<TcfConsent.RestrictionType> restrictionType
    Integer vendorIdGvl
    Integer vendorListVersion
}
