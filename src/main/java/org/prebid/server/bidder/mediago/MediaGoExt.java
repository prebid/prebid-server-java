package org.prebid.server.bidder.mediago;

import lombok.Value;

@Value(staticConstructor = "of")
public class MediaGoExt {

    String token;

    String region;

}
