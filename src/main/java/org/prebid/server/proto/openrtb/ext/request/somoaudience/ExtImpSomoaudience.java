package org.prebid.server.proto.openrtb.ext.request.somoaudience;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSomoaudience {

    String placementHash;
}
