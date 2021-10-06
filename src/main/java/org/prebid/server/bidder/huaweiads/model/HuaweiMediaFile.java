package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HuaweiMediaFile {

    String mime;

    Integer width;

    Integer height;

    Integer fileSize;

    String url;

    String sha256;
}

