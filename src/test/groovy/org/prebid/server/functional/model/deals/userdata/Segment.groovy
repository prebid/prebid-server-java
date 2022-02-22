package org.prebid.server.functional.model.deals.userdata

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Segment {

    String id

    static getDefaultSegment() {
        new Segment(id: PBSUtils.randomString)
    }
}
