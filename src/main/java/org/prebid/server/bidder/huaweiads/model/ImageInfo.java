package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ImageInfo {
    private String url;
    private Integer height;
    private Integer fileSize;
    private String sha256;
    private String ImageType;
    private Integer width;
}
