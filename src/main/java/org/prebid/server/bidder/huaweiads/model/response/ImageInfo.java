package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ImageInfo {

    String url;

    Integer height;

    Long fileSize;

    String sha256;

    String imageType;

    Integer width;
}
