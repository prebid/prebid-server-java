package org.prebid.server.proto.openrtb.ext.request.resetdigital;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpResetDigital {

    String placementId;
}
