package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class Icon {

    String url;

    Long height;

    Long fileSize;

    String sha256;

    String imageType;

    Long width;

}
