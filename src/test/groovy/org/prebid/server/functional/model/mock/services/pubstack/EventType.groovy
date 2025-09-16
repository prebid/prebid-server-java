package org.prebid.server.functional.model.mock.services.pubstack

import com.fasterxml.jackson.annotation.JsonValue

enum EventType {

    AUCTION, COOKIESYNC, AMP, SETUID, VIDEO

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
