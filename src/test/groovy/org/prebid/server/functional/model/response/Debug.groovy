package org.prebid.server.functional.model.response

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.response.auction.HttpCall

@ToString(includeNames = true, ignoreNulls = true)
class Debug {

    Map<String, List<HttpCall>> httpcalls
    BidRequest resolvedrequest

    Map<String, List<HttpCall>> getBidders() {
        def result = httpcalls?.findAll { it.key != "cache" }
        result ?: [:]
    }
}
