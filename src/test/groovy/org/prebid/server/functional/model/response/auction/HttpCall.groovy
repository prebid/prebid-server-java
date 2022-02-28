package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class HttpCall {

    String uri
    String requestbody
    Map<String, List<String>> requestheaders
    String responsebody
    Integer status
}
