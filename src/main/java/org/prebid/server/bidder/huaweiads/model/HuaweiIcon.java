package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HuaweiIcon {
    private String url;
    private Integer height;
    private Integer fileSize;
    private String sha256;
    private String ImageType;
    private Integer width;
}
