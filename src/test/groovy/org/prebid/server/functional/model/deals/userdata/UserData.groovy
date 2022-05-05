package org.prebid.server.functional.model.deals.userdata

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class UserData {

    String id
    String name
    List<Segment> segment

    static UserData getDefaultUserData() {
        new UserData(id: PBSUtils.randomString,
                name: PBSUtils.randomString,
                segment: [Segment.defaultSegment]
        )
    }
}
