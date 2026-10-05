package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class SpecialFeatureConfig {

    Boolean enforce
    List<String> vendorExceptions
    @JsonProperty("vendor_exceptions")
    List<BidderName> vendorExceptionsSnakeCase
}
