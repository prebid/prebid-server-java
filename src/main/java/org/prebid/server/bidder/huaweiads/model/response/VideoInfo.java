package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class VideoInfo {

    String videoDownloadUrl;

    Integer videoDuration;

    Integer videoFileSize;

    String sha256;

    Float videoRatio;

    Integer width;

    Integer height;

}
