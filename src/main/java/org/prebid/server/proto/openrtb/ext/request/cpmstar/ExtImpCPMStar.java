package org.prebid.server.proto.openrtb.ext.request.cpmstar;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpCPMStar {

    Integer placementId;

    Integer subpoolId;
}

