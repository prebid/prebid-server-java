package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class Icon {

    String url;

    Integer height;

    Long fileSize;

    String sha256;

    String imageType;

    Integer width;

}
