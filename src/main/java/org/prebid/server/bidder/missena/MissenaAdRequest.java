package org.prebid.server.bidder.missena;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class MissenaAdRequest {

    String requestId;

    int timeout;

    String referer;

    String refererCanonical;

    String consentString;

    boolean consentRequired;

    String placement;

    String test;
}
