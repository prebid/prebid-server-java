package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtRequestPrebidPbs {

    String endpoint;
}
