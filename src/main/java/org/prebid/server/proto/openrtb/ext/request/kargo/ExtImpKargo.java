package org.prebid.server.proto.openrtb.ext.request.kargo;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpKargo {

    String adSlotID;
}
