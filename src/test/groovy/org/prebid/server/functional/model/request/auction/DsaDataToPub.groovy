package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum DsaDataToPub {

    DO_NOT_SEND_TRANSPARENCY(0),
    OPTIONAL_TO_SEND(1),
    SEND_TRANSPARENCY(2)

    @JsonValue
    final int value

    private DsaDataToPub(int value) {
        this.value = value
    }
}
