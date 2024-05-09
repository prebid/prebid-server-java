package org.prebid.server.proto.openrtb.ext.request.loyal;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpLoyal {

    String placementId;

    String endpointId;
}
