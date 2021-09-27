package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HuaweiVideoInfo {
    private String videoDownloadUrl;
    private Integer videoDuration;
    private Integer videoFileSize;
    private String sha256;
    private Float videoRatio;
    private Integer width;
    private Integer height;
}

