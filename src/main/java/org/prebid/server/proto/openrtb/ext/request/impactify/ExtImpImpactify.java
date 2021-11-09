package org.prebid.server.proto.openrtb.ext.request.impactify;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpImpactify {

    @JsonProperty("appId")
    String appId;

    String format;

    String style;
}
