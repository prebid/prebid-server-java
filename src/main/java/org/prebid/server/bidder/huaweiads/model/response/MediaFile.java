package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class MediaFile {

    String mime;

    Integer width;

    Integer height;

    @JsonProperty("fileSize")
    Long fileSize;

    String url;

    @JsonProperty("sha256")
    String sha256;
}
