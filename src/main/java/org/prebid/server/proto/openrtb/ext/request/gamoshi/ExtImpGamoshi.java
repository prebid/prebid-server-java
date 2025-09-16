package org.prebid.server.proto.openrtb.ext.request.gamoshi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpGamoshi {

    @JsonProperty("supplyPartnerId")
    String supplyPartnerId;

    @JsonProperty("favoredMediaType")
    String favoredMediaType;
}
