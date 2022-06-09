package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class BidderCall {

    String uri
    String requestbody
    @JsonProperty("calltype")
    BidderCallType callType
    Map<String, List<String>> requestheaders
    String responsebody
    Integer status
}
