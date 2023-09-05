package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

@Value(staticConstructor = "of")
public class HuaweiAdm {

    String adm;
    Integer width;
    Integer height;

}
