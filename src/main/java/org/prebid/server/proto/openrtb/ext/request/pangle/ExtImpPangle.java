package org.prebid.server.proto.openrtb.ext.request.pangle;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpPangle {

    String token;

    String appid;

    String placementid;
}
