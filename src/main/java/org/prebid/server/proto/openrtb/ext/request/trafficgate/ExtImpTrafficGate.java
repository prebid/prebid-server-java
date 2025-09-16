package org.prebid.server.proto.openrtb.ext.request.trafficgate;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpTrafficGate {

    String placementId;

    String host;
}
