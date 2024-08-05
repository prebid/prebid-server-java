package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString(includeNames = true, ignoreNulls = true)
class Format {

    @JsonProperty("w")
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
}
