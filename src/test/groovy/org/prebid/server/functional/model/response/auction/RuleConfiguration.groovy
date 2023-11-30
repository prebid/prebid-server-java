package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import groovy.transform.EqualsAndHashCode

import static org.prebid.server.functional.model.request.auction.Condition.ConditionType

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
@EqualsAndHashCode
class RuleConfiguration {

    List<String> componentNames
    List<ConditionType> componentTypes
    Boolean allow
    Boolean gppSidsMatched
    String gpc
    List<GeoCode> geoCodes
    List<And> and
}
