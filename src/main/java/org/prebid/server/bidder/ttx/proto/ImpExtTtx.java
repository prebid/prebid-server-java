package org.prebid.server.bidder.ttx.proto;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ImpExtTtx {

    String prod;

    String zoneid;
}
