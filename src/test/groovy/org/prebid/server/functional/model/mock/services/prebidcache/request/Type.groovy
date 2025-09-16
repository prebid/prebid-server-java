package org.prebid.server.functional.model.mock.services.prebidcache.request

import com.fasterxml.jackson.annotation.JsonValue

enum Type {

    XML, JSON

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
