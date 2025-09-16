package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
class Condition {

    List<ConditionType> componentType
    @JsonProperty("component_type")
    List<ConditionType> componentTypeSnakeCase
    @JsonProperty("component-type")
    List<ConditionType> componentTypeKebabCase
    List<String> componentName
    @JsonProperty("component_name")
    List<String> componentNameSnakeCase
    @JsonProperty("component-name")
    List<String> componentNameKebabCase
    List<Integer> gppSid
    @JsonProperty("gpp_sid")
    List<Integer> gppSidSnakeCase
    @JsonProperty("gpp-sid")
    List<Integer> gppSidKebabCase
    List<String> geo
    String gpc

    static Condition getBaseCondition(String componentName = BidderName.GENERIC.value) {
        new Condition().tap {
            it.componentType = [ConditionType.BIDDER]
            it.componentName = [componentName]
        }
    }

    enum ConditionType {
        BIDDER("bidder"),
        GENERAL_MODULE("general"),
        RTD_MODULE("rtd"),
        ANALYTICS("analytics")

        final String name

        private ConditionType(String name) {
            this.name = name
        }
    }
}
