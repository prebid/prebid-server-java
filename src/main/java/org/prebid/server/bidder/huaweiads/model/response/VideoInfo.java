package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder(toBuilder = true)
@Value
public class VideoInfo {

    @JsonProperty("videoDownloadUrl")
    String videoDownloadUrl;

    @JsonProperty("videoDuration")
    Long videoDuration;

    @JsonProperty("videoFileSize")
    Integer videoFileSize;

    @JsonProperty("sha256")
    String sha256;

    @JsonProperty("videoRatio")
    BigDecimal videoRatio;

    @JsonProperty("width")
    Integer width;

    @JsonProperty("height")
    Integer height;
}
