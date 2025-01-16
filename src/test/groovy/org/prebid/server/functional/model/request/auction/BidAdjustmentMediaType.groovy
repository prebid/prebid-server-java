package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum BidAdjustmentMediaType {

    BANNER("banner"),
    AUDIO("audio"),
    NATIVE("native"),
    VIDEO("video"),
    VIDEO_IN_STREAM("video-instream"),
    VIDEO_OUT_STREAM("video-outstream"),
    ANY('*'),
    UNKNOWN('unknown')

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
