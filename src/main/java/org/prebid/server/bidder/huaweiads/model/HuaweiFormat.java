package org.prebid.server.bidder.huaweiads.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class HuaweiFormat {

    Integer w;

    Integer h;
}

