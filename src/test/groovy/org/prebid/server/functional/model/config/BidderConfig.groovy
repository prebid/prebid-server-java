package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class BidderConfig {

    Boolean enabled
    List<BidderName> allowedBidderCodes
    @JsonProperty("allowedbiddercodes")
    List<BidderName> allowedBidderCodesLowerCase
}
