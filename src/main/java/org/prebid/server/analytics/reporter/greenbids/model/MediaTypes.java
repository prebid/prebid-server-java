package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import lombok.Value;

@Value(staticConstructor = "of")
public class MediaTypes {

    ExtBanner banner;

    Video video;

    @JsonProperty("native")
    Native nativeObject;
}
