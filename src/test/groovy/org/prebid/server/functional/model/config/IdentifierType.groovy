package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

import static org.prebid.server.functional.model.config.OperatingSystem.ANDROID
import static org.prebid.server.functional.model.config.OperatingSystem.FIRE
import static org.prebid.server.functional.model.config.OperatingSystem.IOS
import static org.prebid.server.functional.model.config.OperatingSystem.ROKU
import static org.prebid.server.functional.model.config.OperatingSystem.TIZEN

enum IdentifierType {

    EMAIL_ADDRESS("e"),
    PHONE_NUMBER("p"),
    POSTAL_CODE("z"),
    APPLE_IDFA("a"),
    GOOGLE_GAID("g"),
    ROKU_RIDA("r"),
    SAMSUNG_TIFA("s"),
    AMAZON_AFAI("f"),
    NET_ID("n"),
    ID5("id5"),
    UTIQ("utiq"),
    OPTABLE_VID("v")

    @JsonValue
    final String value

    IdentifierType(String value) {
        this.value = value
    }

    static IdentifierType fromOS(OperatingSystem os) {
        switch (os) {
            case IOS:
                return APPLE_IDFA
            case ANDROID:
                return GOOGLE_GAID
            case ROKU:
                return ROKU_RIDA
            case TIZEN:
                return SAMSUNG_TIFA
            case FIRE:
                return AMAZON_AFAI
            default:
                throw new IllegalArgumentException("Unsupported OS: " + os);
        }
    }
}
