package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString
import org.prebid.server.functional.util.Case
import org.prebid.server.functional.util.PBSUtils

@ToString
enum PurposeOneTreatmentInterpretation {

    IGNORE("ignore"),
    NO_ACCESS_ALLOWED("no-access-allowed"),
    ACCESS_ALLOWED("access-allowed")

    final String value

    PurposeOneTreatmentInterpretation(String value) {
        this.value = value
    }

    @JsonValue
    String toString() {
        def type = PBSUtils.getRandomEnum(Case.class)
        if (type.SNAKE) {
            return PBSUtils.convertCase(value, Case.SNAKE)
        }
        return value
    }
}
