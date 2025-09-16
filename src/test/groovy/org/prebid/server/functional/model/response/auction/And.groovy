package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.PrivacyModule

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class And {

    List<String> and
    PrivacyModule privacyModule
    Boolean skipped
    RuleResult result
}
