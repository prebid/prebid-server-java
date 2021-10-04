package org.prebid.server.bidder.huaweiads.model.xnative;

import lombok.Value;

@Value(staticConstructor = "of")
public class XnativeImage {

    Integer imageAssetType;

    String url;

    Integer w;

    Integer h;
}
