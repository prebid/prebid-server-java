package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class SiteExt {

    @JsonProperty("amp")
    Boolean isAmp
    SiteExtData data

    static SiteExt getFPDSiteExt() {
        new SiteExt(data: SiteExtData.FPDSiteExtData)
    }

    @JsonValue
    Integer getAmp() {
        isAmp ? 1 : 0
    }
}
