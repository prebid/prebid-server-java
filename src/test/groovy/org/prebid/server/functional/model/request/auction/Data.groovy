package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import groovy.transform.EqualsAndHashCode

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Data {

    String id
    String name
    List<Segment> segment
}
