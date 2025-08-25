package org.prebid.server.bidder.mediasquare.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class MediasquareGdpr {

    boolean consentRequired;

    String consentString;
}
