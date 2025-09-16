package org.prebid.server.proto.openrtb.ext.request.nextmillennium;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpNextMillennium {

    String placementId;

    String groupId;
}
