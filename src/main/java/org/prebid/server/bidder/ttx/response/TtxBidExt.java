package org.prebid.server.bidder.ttx.response;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class TtxBidExt {

    TtxBidExtTtx ttx;
}
