package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class MediaFile {
    private String mime;
    private Integer width;
    private Integer height;
    private Integer fileSize;
    private String url;
    private String sha256;
}
