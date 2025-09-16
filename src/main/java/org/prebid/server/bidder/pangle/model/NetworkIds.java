package org.prebid.server.bidder.pangle.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class NetworkIds {

    String appid;

    String placementid;
}
