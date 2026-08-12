package org.prebid.server.bidder.magnite.proto.request;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class MagniteUserExt {

    MagniteUserExtRp rp;
}
