package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = false)
class Schain {

    String ver
    Integer complete
    List<SchainNode> nodes
}
