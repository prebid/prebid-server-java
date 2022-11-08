package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.response.auction.MediaType

import static org.prebid.server.functional.model.response.auction.MediaType.BANNER
import static org.prebid.server.functional.model.response.auction.MediaType.NATIVE
import static org.prebid.server.functional.model.response.auction.MediaType.VIDEO

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

    static Imp getDefaultImpression(MediaType mediaType = BANNER) {
        switch (mediaType) {
            case BANNER:
                return defaultImp.tap {
                    banner = Banner.defaultBanner
                }
            case VIDEO:
                return defaultImp.tap {
                    video = Video.defaultVideo
                }
            case NATIVE:
                return defaultImp.tap {
                    nativeObj = Native.defaultNative
                }
            default:
                return defaultImp.tap {
                    banner = Banner.defaultBanner
                }
        }
    }

    private static Imp getDefaultImp() {
        new Imp().tap {
            id = UUID.randomUUID()
            ext = ImpExt.defaultImpExt
        }
    }
}
