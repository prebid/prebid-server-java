package org.prebid.server.proto.openrtb.ext.request.inmobi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpInmobi {

    @JsonProperty("plc")
    String plc;
}
