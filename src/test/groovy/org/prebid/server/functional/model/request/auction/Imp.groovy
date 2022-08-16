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
    List<Metric> metric
    Banner banner
    Video video
    Audio audio
    @JsonProperty("native")
    Native nativeObj
    Pmp pmp
    String displayManager
    String displayManagerVer
    Integer instl
    String tagId
    BigDecimal bidFloor
    Currency bidFloorCur
    Integer clickBrowser
    Integer secure
    List<String> iframeBuster
    Integer rwdd
    Integer ssai
    Integer exp
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

    static Imp getNativeImpression() {
        defaultImp.tap {
            nativeObj = Native.defaultNative
        }
    }

   private static Imp getDefaultImp() {
        new Imp().tap {
            id = UUID.randomUUID()
            ext = ImpExt.defaultImpExt
        }
    }
}
