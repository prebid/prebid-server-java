package org.prebid.server.functional.model.privacy

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeEnforcement
import org.prebid.server.functional.util.privacy.TcfConsent

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class EnforcementRequirement {

    Purpose purpose
    PurposeEnforcement enforcePurpose
    @JsonProperty("enforce_purpose")
    PurposeEnforcement enforcePurposeSnakeCase
    Boolean enforceVendor
    @JsonProperty("enforce_vendor")
    Boolean enforceVendorSnakeCase
    Integer vendorConsentBitField
    Integer vendorLegitimateInterestBitField
    List<BidderName> vendorExceptions
    boolean purposesLITransparency
    List<TcfConsent.RestrictionType> restrictionType
    Integer vendorIdGvl
    Integer vendorListVersion
}
