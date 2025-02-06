package org.prebid.server.functional.model.mock.services.vendorlist

import com.fasterxml.jackson.annotation.JsonValue

enum GvlSpecificationVersion {

    V2(2), V3(3)

    @JsonValue
    private final Integer value

    GvlSpecificationVersion(Integer value) {
        this.value = value
    }
}
