package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class BidderCall {

    String uri
    String requestbody
    BidderCallType calltype
    Map<String, List<String>> requestheaders
    String responsebody
    Integer status
}
