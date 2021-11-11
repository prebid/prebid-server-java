package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Source {

    Integer fd
    String tid
    String pchain
    SourceExt ext
}
