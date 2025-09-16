package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Data {

    String id
    String name
    List<Segment> segment
    ExtData ext

    static Data getDefaultData() {
        new Data().tap {
            id = PBSUtils.randomString
            name = PBSUtils.randomString
        }
    }
}
