package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
class DeviceExt {

    Atts atts
    String cdep
    @JsonProperty("ifa_type")
    String ifaType

    enum Atts {

        UNKNOWN(0),
        RESTRICTED(1),
        DENIED(2),
        AUTHORIZED(3),

        @JsonValue
        int value

        Atts(int value) {
            this.value = value
        }

        @Override
        String toString() {
            value
        }
    }
}
