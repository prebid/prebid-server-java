package org.prebid.server.proto.openrtb.ext.request.gamoshi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpGamoshi {

    @JsonProperty("supplyPartnerId")
    String supplyPartnerId;

    @JsonProperty("favoredMediaType")
    String favoredMediaType;
}
