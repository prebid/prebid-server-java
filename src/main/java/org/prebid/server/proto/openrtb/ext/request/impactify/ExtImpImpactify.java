package org.prebid.server.proto.openrtb.ext.request.impactify;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpImpactify {

    @JsonProperty("appId")
    String appId;

    String format;

    String style;
}
