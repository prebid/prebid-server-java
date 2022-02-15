package org.prebid.server.functional.model.deals.lineitem.targeting

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.PackageScope

@PackageScope
class MatchingFunctionNode {

    Map<MatchingFunction, List<?>> matchingFunctionMultipleValuesNode

    Map<MatchingFunction, ?> matchingFunctionSingleValueNode

    @JsonValue
    def getMatchingFunctionNode() {
        matchingFunctionMultipleValuesNode ?: matchingFunctionSingleValueNode
    }
}
