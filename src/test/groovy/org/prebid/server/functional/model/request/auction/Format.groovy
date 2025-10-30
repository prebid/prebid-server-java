package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Format {

    @JsonProperty("w")
    Integer width
    @JsonProperty("h")
    Integer height
    @JsonProperty("wratio")
    Integer widthRatio
    @JsonProperty("hratio")
    Integer heightRatio
    @JsonProperty("wmin")
    Integer widthMin

    static Format getDefaultFormat() {
        new Format().tap {
            width = 300
            height = 250
        }
    }

    static Format getRandomFormat() {
        new Format().tap {
            width = PBSUtils.randomNumber
            height = PBSUtils.randomNumber
        }
    }
}
