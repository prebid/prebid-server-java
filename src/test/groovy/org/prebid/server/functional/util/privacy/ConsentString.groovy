package org.prebid.server.functional.util.privacy

import com.fasterxml.jackson.annotation.JsonValue

interface ConsentString {

    @JsonValue
    String getConsentString()
}
