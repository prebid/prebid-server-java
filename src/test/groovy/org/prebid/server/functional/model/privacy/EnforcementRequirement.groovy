package org.prebid.server.functional.model.privacy

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.config.Purpose
import org.prebid.server.functional.model.config.PurposeEnforcement
import org.prebid.server.functional.util.privacy.TcfConsent

import java.time.ZonedDateTime

import static org.prebid.server.functional.util.privacy.TcfConsent.RestrictionType.REQUIRE_CONSENT
import static org.prebid.server.functional.util.privacy.TcfConsent.RestrictionType.UNDEFINED

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
    List<Integer> disclosedVendorsId
    ZonedDateTime created = ZonedDateTime.now()
    ZonedDateTime updated = ZonedDateTime.now()

    static EnforcementRequirement getDefaultBase(Integer disclosedVendorsId, Purpose purpose = Purpose.P2) {
        new EnforcementRequirement().tap {
            it.purpose = purpose
            it.enforcePurpose = PurposeEnforcement.BASIC
            it.enforceVendor = false
            it.disclosedVendorsId = [disclosedVendorsId]
        }
    }

    static EnforcementRequirement getDefaultFull(Integer vendorId,
                                                 Integer vendorListVersion,
                                                 Purpose purpose = Purpose.P2) {

        new EnforcementRequirement().tap {
            it.enforcePurpose = PurposeEnforcement.FULL
            it.purpose = purpose
            it.enforceVendor = true
            it.vendorIdGvl = vendorId
            it.restrictionType = [REQUIRE_CONSENT, UNDEFINED]
            it.vendorConsentBitField = vendorId
            it.vendorListVersion = vendorListVersion
            it.disclosedVendorsId = [vendorId]
        }
    }
}
