package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HuaweiVideoInfo {

    String videoDownloadUrl;
    Integer videoDuration;
    Integer videoFileSize;
    String sha256;
    Float videoRatio;
    int width;
    int height;
}

