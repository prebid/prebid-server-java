package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency

@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
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
    String displayManager
    String displayManagerVer
    Integer instl
    String tagId
    Integer clickBrowser
    Integer secure
    List<String> iframeBuster
    Integer exp
    BigDecimal bidFloor
    Currency bidFloorCur
    ImpExt ext

    static Imp getDefaultImpression() {
        defaultImp.tap {
            banner = Banner.defaultBanner
        }
    }

    static Imp getVideoImpression() {
        defaultImp.tap {
            video = Video.defaultVideo
        }
    }

    private static Imp getDefaultImp() {
        new Imp().tap {
            id = UUID.randomUUID()
            ext = ImpExt.defaultImpExt
        }
    }
}
