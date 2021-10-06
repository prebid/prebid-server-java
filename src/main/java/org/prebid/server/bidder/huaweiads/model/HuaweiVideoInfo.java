package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HuaweiVideoInfo {

    String videoDownloadUrl;

    Integer videoDuration;

    Integer videoFileSize;

    String sha256;

    Float videoRatio;

    Integer width;

    Integer height;
}

