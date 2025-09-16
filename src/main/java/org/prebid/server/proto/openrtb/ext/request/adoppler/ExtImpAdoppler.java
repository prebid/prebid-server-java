package org.prebid.server.proto.openrtb.ext.request.adoppler;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdoppler {

    String adunit;
    String client;
}
