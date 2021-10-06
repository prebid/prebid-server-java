package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HuaweiIcon {

    String url;

    Integer height;

    Integer fileSize;

    String sha256;

    String imageType;

    Integer width;
}

