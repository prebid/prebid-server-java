package org.prebid.server.proto.openrtb.ext.request.floxis;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpFloxis {

    String seat;

    String region;

    String partner;
}
