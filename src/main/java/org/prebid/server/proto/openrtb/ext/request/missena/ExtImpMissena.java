package org.prebid.server.proto.openrtb.ext.request.missena;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpMissena {

    @JsonProperty("apiKey")
    String apiKey;

    String placement;

    @JsonProperty("test")
    String testMode;
}
