package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder(toBuilder = true)
@Value
public class VideoInfo {

    String videoDownloadUrl;

    Long videoDuration;

    Integer videoFileSize;

    String sha256;

    BigDecimal videoRatio;

    Integer width;

    Integer height;
}
