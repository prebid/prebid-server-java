package org.prebid.server.proto.openrtb.ext.request.sparteo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import lombok.Value;

@Value(staticConstructor = "of")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtImpSparteo {

    @NonNull
    @JsonProperty("networkId")
    String networkId;

    @JsonProperty("custom1")
    String custom1;

    @JsonProperty("custom2")
    String custom2;

    @JsonProperty("custom3")
    String custom3;

    @JsonProperty("custom4")
    String custom4;

    @JsonProperty("custom5")
    String custom5;
}
