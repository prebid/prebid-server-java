package org.prebid.server.functional.model.request.logging.httpinteraction

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
class HttpInteractionRequest {

    String endpoint
    String statusCode
    String account
    Integer limit
    BidderName bidder

    static HttpInteractionRequest getDefaultHttpInteractionRequest() {
        def request = new HttpInteractionRequest()
        request.limit = 1 // Using this number as there is no point in capturing more requests by default
        request
    }
}
