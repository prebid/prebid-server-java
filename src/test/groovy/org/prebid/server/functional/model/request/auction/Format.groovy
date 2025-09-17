package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
<<<<<<< HEAD
import org.prebid.server.functional.util.PBSUtils
=======
>>>>>>> 04d9d4a13 (Initial commit)

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Format {

    @JsonProperty("w")
<<<<<<< HEAD
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
=======
    Integer weight
    @JsonProperty("h")
    Integer height
    @JsonProperty("wratio")
    Integer weightRatio
    @JsonProperty("hratio")
    Integer heightRatio
    @JsonProperty("wmin")
    Integer weightMin

    static Format getDefaultFormat() {
        new Format().tap {
            weight = 300
            height = 250
        }
    }
>>>>>>> 04d9d4a13 (Initial commit)
}
