package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BidAdjustmentMediaType {

    BANNER,
    AUDIO,
    X_NATIVE,
    VIDEO,
    VIDEO_OUTSTREAM;

    @JsonValue
    @Override
    public String toString() {
        return this == X_NATIVE ? "native"
                : this == VIDEO_OUTSTREAM ? "video-outstream"
                : super.toString().toLowerCase();
    }
}
