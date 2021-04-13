package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AdjustmentsMediaType {

    banner,
    audio,
    @JsonProperty("native")
    xNative,
    video,
    @JsonProperty("video-outstream")
    video_outstream
}
