package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Native {

    String request
    String ver
    List<Integer> api
    List<Integer> battr
}
