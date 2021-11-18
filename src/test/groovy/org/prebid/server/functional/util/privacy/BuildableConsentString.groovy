package org.prebid.server.functional.util.privacy

import com.fasterxml.jackson.annotation.JsonValue

interface BuildableConsentString {

    @JsonValue
    String getConsentString()
}
