package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class MediaTypes {

    @JsonProperty("banner")
    ExtBanner banner;

    @JsonProperty("video")
    Video video;

    @JsonProperty("native")
    Native nativeObject;
}
