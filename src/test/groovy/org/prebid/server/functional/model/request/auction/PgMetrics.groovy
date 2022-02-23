package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class PgMetrics {

    Set<String> sentToClient
    Set<String> sentToClientAsTopMatch
    Set<String> matchedDomainTargeting
    Set<String> matchedWholeTargeting
    Set<String> matchedTargetingFcapped
    Set<String> matchedTargetingFcapLookupFailed
    Set<String> readyToServe
    Set<String> pacingDeferred
    Map<String, Set<String>> sentToBidder
    Map<String, Set<String>> sentToBidderAsTopMatch
    Map<String, Set<String>> receivedFromBidder
    Set<String> responseInvalidated
}
