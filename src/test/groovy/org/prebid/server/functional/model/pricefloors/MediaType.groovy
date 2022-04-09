package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonValue

enum MediaType {

    BANNER("banner"),
    VIDE_OUTSTREAM("video-outstream"),
    VIDEO_INSTREAM("video-instream"),
    VIDEO("video"),
    NATIVE_MEDIA_TYPE("native"),
    AUDIO("audio"),
    MULTIPLE("*")

    @JsonValue
    final String value

    MediaType(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
