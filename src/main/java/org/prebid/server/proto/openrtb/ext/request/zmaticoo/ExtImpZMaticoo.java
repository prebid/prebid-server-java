package org.prebid.server.proto.openrtb.ext.request.zmaticoo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpZMaticoo {

    @JsonProperty("pubId")
    String pubId;

    @JsonProperty("zoneId")
    String zoneId;
}
