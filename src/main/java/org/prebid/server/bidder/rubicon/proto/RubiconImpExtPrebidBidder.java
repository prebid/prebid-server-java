package org.prebid.server.bidder.rubicon.proto;

import lombok.Value;

@Value(staticConstructor = "of")
public class RubiconImpExtPrebidBidder {

    RubiconImpExtPrebidRubiconDebug debug;
}
