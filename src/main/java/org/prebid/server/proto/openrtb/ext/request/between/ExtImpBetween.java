package org.prebid.server.proto.openrtb.ext.request.between;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBetween {

    String host;

    String publisherId;
}
