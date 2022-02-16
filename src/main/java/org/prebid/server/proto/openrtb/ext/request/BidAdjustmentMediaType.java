package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BidAdjustmentMediaType {

    @JsonProperty("banner")
    BANNER,
    @JsonProperty("audio")
    AUDIO,
    @JsonProperty("native")
    X_NATIVE,
    @JsonProperty("video")
    VIDEO,
    @JsonProperty("video-outstream")
    VIDEO_OUTSTREAM;

    @Override
    public String toString() {
        return this == X_NATIVE ? "native"
                : this == VIDEO_OUTSTREAM ? "video-outstream"
                : super.toString().toLowerCase();
    }
}
