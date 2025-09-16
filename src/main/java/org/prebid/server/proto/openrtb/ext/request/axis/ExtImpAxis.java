package org.prebid.server.proto.openrtb.ext.request.axis;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAxis {

    String integration;

    String token;
}
