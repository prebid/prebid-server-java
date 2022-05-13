package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum BidAdjustmentMediaType {

    BANNER("banner"),
    AUDIO("audio"),
    NATIVE("native"),
    VIDEO("video"),
    VIDEO_OUTSTREAM("video-outstream")

    @JsonValue
    String value

    BidAdjustmentMediaType(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
