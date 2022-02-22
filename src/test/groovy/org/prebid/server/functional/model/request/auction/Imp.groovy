package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Imp {

    String id
    Banner banner
    List<Metric> metric
    Video video
    Audio audio
    @JsonProperty("native")
    Native nativeObj
    Pmp pmp
    String displaymanager
    String displaymanagerver
    Integer instl
    String tagid
    BigDecimal bidfloor
    String bidfloorcur
    Integer clickbrowser
    Integer secure
    List<String> iframebuster
    Integer exp
    ImpExt ext

    static Imp getDefaultImpression() {
        getDefaultImp().tap {
            banner = Banner.getDefaultBanner()
        }
    }

    private static Imp getDefaultImp() {
        new Imp().tap {
            id = UUID.randomUUID()
            ext = ImpExt.defaultImpExt
        }
    }
}
