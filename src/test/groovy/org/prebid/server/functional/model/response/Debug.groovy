package org.prebid.server.functional.model.response

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.PgMetrics
import org.prebid.server.functional.model.response.auction.DebugPrivacy
import org.prebid.server.functional.model.response.auction.BidderCall

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class Debug {

    Map<String, List<BidderCall>> httpcalls
    BidRequest resolvedRequest
    DebugPrivacy privacy
    PgMetrics pgmetrics

    Map<String, List<BidderCall>> getBidders() {
        def result = httpcalls?.findAll { it.key != "cache" }
        result ?: [:]
    }
}
