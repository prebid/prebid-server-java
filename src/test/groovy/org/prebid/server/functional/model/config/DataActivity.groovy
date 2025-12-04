package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.model.privacy.gpp.GppDataActivity

enum DataActivity {

    NOT_APPLICABLE(0),
    NOTICE_PROVIDED(1),
    NOTICE_NOT_PROVIDED(2),
    NO_CONSENT(1),
    CONSENT(2),
    INVALID(-1)

    static DataActivity fromGppDataActivity(GppDataActivity gppActivity) {
        if (gppActivity == null) {
            return INVALID;
        }
        switch (gppActivity) {
            case GppDataActivity.NOT_APPLICABLE:
                return NOT_APPLICABLE
            case GppDataActivity.NO_CONSENT:
                return NO_CONSENT
            case GppDataActivity.CONSENT:
                return CONSENT
            default:
                return INVALID
        }
    }

    @JsonValue
    final int dataActivityBits

    DataActivity(int dataActivityBits) {
        this.dataActivityBits = dataActivityBits
    }

    static DataActivity fromInt(int dataActivityBits) {
        values().find { it.dataActivityBits == dataActivityBits }
                ?: { throw new IllegalArgumentException("Invalid dataActivityBits: ${dataActivityBits}") }
    }
}
