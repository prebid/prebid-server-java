package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class DoohExt {

    DoohExtData data

    static DoohExt getDefaultDoohExt() {
        new DoohExt(data: DoohExtData.defaultDoohExtData)
    }
}
