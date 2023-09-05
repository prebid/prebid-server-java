package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class MediaFile {

    @JsonProperty("mime")
    String mime;

    @JsonProperty("width")
    Integer width;

    @JsonProperty("height")
    Integer height;

    @JsonProperty("fileSize")
    Long fileSize;

    @JsonProperty("url")
    String url;

    @JsonProperty("sha256")
    String sha256;
}
