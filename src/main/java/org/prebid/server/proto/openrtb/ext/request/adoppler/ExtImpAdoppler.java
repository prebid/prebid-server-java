package org.prebid.server.proto.openrtb.ext.request.adoppler;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdoppler {

    String adunit;
    String client;
}
