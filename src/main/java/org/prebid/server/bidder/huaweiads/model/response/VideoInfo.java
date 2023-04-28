package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class VideoInfo {

    String videoDownloadUrl;

    Integer videoDuration;

    Integer videoFileSize;

    String sha256;

    BigDecimal videoRatio;

    Integer width;

    Integer height;

}
