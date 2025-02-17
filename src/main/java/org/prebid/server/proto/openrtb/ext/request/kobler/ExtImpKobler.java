package org.prebid.server.proto.openrtb.ext.request.kobler;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpKobler {

    @JsonProperty("test")
    Boolean test;
}
