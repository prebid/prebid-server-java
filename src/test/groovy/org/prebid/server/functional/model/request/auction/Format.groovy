package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Format {

    Integer w
    Integer h
    Integer wratio
    Integer hratio
    Integer wmin

    static Format getDefaultFormat() {
        new Format().tap {
            w = 300
            h = 250
        }
    }
}
