package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum PrivacyModule {

    IAB_TFC_EU("iab.tcfeu"),
    IAB_US_GENERIC("iab.usgeneric"),
    IAB_ALL("iab.*"),
    CUSTOM_US_UTAH("custom.usutah"),
    ALL("*")

    @JsonValue
    final String value

    private PrivacyModule(String value) {
        this.value = value
    }
}
