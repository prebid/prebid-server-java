package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
<<<<<<< HEAD
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
=======
import groovy.transform.ToString

>>>>>>> 04d9d4a13 (Initial commit)
@ToString(includeNames = true, ignoreNulls = true)
class Banner {

    List<Format> format
    @JsonProperty("w")
<<<<<<< HEAD
    Integer width
=======
    Integer weight
>>>>>>> 04d9d4a13 (Initial commit)
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
