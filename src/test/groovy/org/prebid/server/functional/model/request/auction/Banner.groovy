package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Banner {

    List<Format> format
    @JsonProperty("w")
    Integer weight
    @JsonProperty("h")
    Integer height
    List<Integer> btype
    List<Integer> battr
    Integer pos
    List<String> mimes
    Integer topframe
    List<Integer> expdir
    List<Integer> api
    String id
    Integer vcm

    static Banner getDefaultBanner() {
        new Banner().tap {
            addFormat(Format.defaultFormat)
        }
    }

    void addFormat(Format format) {
        if (this.format == null) {
            this.format = []
        }
        this.format.add(format)
    }
}
