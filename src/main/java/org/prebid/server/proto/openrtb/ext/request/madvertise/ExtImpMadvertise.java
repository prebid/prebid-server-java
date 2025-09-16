package org.prebid.server.proto.openrtb.ext.request.madvertise;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpMadvertise {

    @JsonProperty("zoneId")
    String zoneId;
}
