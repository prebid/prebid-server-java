package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class BidderCall {

    String uri
    String requestBody
    BidderCallType callType
    Map<String, List<String>> requestHeaders
    String responseBody
    Integer status
}
