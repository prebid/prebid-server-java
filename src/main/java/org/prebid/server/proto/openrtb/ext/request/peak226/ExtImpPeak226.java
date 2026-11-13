package org.prebid.server.proto.openrtb.ext.request.peak226;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpPeak226 {

    String publisherId;

    String placementId;

    String region;
}
