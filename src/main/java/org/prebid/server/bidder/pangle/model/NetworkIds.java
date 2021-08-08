package org.prebid.server.bidder.pangle.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class NetworkIds {

    String appid;

    String placementid;
}
