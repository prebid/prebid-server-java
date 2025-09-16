package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = false)
class Segment {

    String id
    String name
    String value
}
