package org.prebid.server.bidder.resetdigital.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class ResetDigitalImpExt {

    String gpid;
}
