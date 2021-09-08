package org.prebid.server.proto.openrtb.ext.request.bmtm;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpBmtm {

    String placementId;
}
