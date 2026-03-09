package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class SiteExt {

    @JsonIgnore
    Boolean isAmp
    SiteExtData data

    static SiteExt getFPDSiteExt() {
        new SiteExt(data: SiteExtData.FPDSiteExtData)
    }

    @JsonProperty("amp")
    Integer getGetAmp() {
        this.isAmp ? 1 : 0
    }

    @JsonProperty("amp")
    void setGetAmp(Integer amp) {
        this.isAmp = (amp == 1)
    }
}
