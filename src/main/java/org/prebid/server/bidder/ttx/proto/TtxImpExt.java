package org.prebid.server.bidder.ttx.proto;

import lombok.Value;

@Value(staticConstructor = "of")
public class TtxImpExt {

    TtxImpExtTtx ttx;
}
