package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ImpMediaType {

    banner,
    audio,
    @JsonProperty("native")
    xNative,
    video,
    @JsonProperty("video-instream")
    video_instream,
    @JsonProperty("video-outstream")
    video_outstream;

    @Override
    public String toString() {
        return this == xNative ? "native"
                : this == video_outstream ? "video-outstream"
                : this == video_instream ? "video-instream"
                : super.toString();
    }
}
