package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class PurposeConfig {

    PurposeEnforcement enforcePurpose
    @JsonProperty("enforce_purpose")
    PurposeEnforcement enforcePurposeSnakeCase
    Boolean enforceVendors
    @JsonProperty("enforce_vendors")
    Boolean enforceVendorsSnakeCase
    List<String> vendorExceptions
    @JsonProperty("vendor_exceptions")
    List<String> vendorExceptionsSnakeCase
    PurposeEid eid
}
