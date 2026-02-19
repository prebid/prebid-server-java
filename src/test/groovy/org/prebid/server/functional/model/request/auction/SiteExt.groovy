package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class SiteExt {

    Amp amp
    SiteExtData data

    static SiteExt getFPDSiteExt() {
        new SiteExt(data: SiteExtData.FPDSiteExtData)
    }

    @ToString(includeNames = true, ignoreNulls = true)
    enum Amp {

        FROM_AMP(1),
        NOT_FROM_AMP(0)

        @JsonValue
        final int code

        Amp(Integer code) {
            this.code == code
        }
    }
}
