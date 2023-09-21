package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ImageInfo {

    String url;

    Integer height;

    Integer width;

    @JsonProperty("fileSize")
    Long fileSize;

    @JsonProperty("sha256")
    String sha256;

    @JsonProperty("imageType")
    String imageType;

}
