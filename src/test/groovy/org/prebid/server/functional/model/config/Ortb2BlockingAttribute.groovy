package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum Ortb2BlockingAttribute {

    BADV, BAPP, BATTR, BCAT, BTYPE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
