package org.prebid.server.proto.openrtb.ext.request.bigoad;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBigoad {

    @JsonProperty("sspid")
    String sspId;
}
