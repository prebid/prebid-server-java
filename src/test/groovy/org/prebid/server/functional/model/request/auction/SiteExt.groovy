package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class SiteExt {

    Integer amp
    SiteExtData data
}
