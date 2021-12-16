package org.prebid.server.proto.openrtb.ext.request.madvertise;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpMadvertise {

    @JsonProperty("zoneId")
    String zoneId;
}
