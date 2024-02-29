package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class SiteExt {

    Integer amp
    SiteExtData data

    static SiteExt getFPDSiteExt() {
        new SiteExt(data: SiteExtData.FPDSiteExtData)
    }
}
