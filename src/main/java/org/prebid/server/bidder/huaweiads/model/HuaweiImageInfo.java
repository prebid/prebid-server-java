package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HuaweiImageInfo {

    String url;
    Integer height;
    Integer fileSize;
    String sha256;
    String imageType;
    Integer width;
}
