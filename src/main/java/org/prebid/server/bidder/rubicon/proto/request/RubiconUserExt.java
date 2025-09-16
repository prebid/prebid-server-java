package org.prebid.server.bidder.rubicon.proto.request;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class RubiconUserExt {

    RubiconUserExtRp rp;
}
