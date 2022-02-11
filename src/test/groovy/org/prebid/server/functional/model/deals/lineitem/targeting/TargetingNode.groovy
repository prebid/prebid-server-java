package org.prebid.server.functional.model.deals.lineitem.targeting

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.PackageScope
import groovy.transform.TupleConstructor

@PackageScope
@TupleConstructor
class TargetingNode {

    @JsonValue
    Map<TargetingType, MatchingFunctionNode> targetingNode
}
