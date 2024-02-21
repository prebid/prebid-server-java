package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class DoohExtData {

    String data
    String language

    static DoohExtData getDefaultDoohExtData() {
        new DoohExtData().tap {
            data = PBSUtils.randomString
        }
    }
}
