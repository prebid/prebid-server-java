package org.prebid.server.bidder.magnite.proto.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class MagniteBannerExt {

    MagniteBannerExtRp rp;
}
