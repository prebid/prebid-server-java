package org.prebid.server.proto.openrtb.ext.request.liftoff;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpLiftoff {

    String bidToken;

    String appStoreId;

    String placementReferenceId;
}
