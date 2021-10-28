package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Banner {

    List<Format> format
    Integer w
    Integer h
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
