package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum PurposeOneTreatmentInterpretation {

    IGNORE("ignore"),
    NO_ACCESS_ALLOWED("no-access-allowed"),
    ACCESS_ALLOWED("access-allowed")

    @JsonValue
    final String value

    PurposeOneTreatmentInterpretation(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
