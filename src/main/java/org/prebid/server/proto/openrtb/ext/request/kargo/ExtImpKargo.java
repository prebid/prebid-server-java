package org.prebid.server.proto.openrtb.ext.request.kargo;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpKargo {

    String adSlotID;
}
