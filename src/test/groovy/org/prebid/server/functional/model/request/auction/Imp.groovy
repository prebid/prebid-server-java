package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.response.auction.MediaType

import static org.prebid.server.functional.model.response.auction.MediaType.AUDIO
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
    OperationState instl
    String tagId
    BigDecimal bidFloor
    Currency bidFloorCur
    Integer clickBrowser
    SecurityLevel secure
    List<String> iframeBuster
    Integer rwdd
    Integer ssai
    Integer exp
    Qty qty
    Double dt
    Refresh refresh
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
            case AUDIO:
                return defaultImp.tap {
                    audio = Audio.defaultAudio
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

    @JsonIgnore
    BidderName getBidderName() {
        def bidder = ext?.prebid?.bidder
        if (!bidder) {
            throw new IllegalStateException("No bidder found")
        }

        def bidderNames = [
                (bidder.alias)           : BidderName.ALIAS,
                (bidder.generic)         : BidderName.GENERIC,
                (bidder.genericCamelCase): BidderName.GENERIC_CAMEL_CASE,
                (bidder.rubicon)         : BidderName.RUBICON,
                (bidder.appNexus)        : BidderName.APPNEXUS,
                (bidder.openx)           : BidderName.OPENX,
                (bidder.ix)              : BidderName.IX
        ].findAll { it.key }

        if (bidderNames.size() != 1) {
            throw new IllegalStateException("Invalid number of bidders: ${bidderNames.size()}")
        }

        bidderNames.values().first()
    }

    @JsonIgnore
    List<MediaType> getMediaTypes() {
        return [
                (banner ? BANNER : null),
                (video ? VIDEO : null),
                (nativeObj ? NATIVE : null),
                (audio ? AUDIO : null)
        ].findAll { it }
    }
}
