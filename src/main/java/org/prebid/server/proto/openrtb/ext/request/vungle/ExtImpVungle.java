package org.prebid.server.proto.openrtb.ext.request.vungle;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpVungle {

    String bidToken;

    String appStoreId;

    String placementReferenceId;
}
