package org.prebid.server.proto.openrtb.ext.request.bmtm;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBmtm {

    String placementId;
}
