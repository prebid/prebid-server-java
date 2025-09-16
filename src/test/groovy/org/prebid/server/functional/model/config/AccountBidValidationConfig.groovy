package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class AccountBidValidationConfig {

    @JsonProperty("banner-creative-max-size")
    BidValidationEnforcement bannerMaxSizeEnforcement
    @JsonProperty("banner_creative_max_size")
    BidValidationEnforcement bannerMaxSizeEnforcementSnakeCase
}
