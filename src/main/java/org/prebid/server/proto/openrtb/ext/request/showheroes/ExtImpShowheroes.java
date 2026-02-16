package org.prebid.server.proto.openrtb.ext.request.showheroes;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpShowheroes {

    @JsonProperty("unitId")
    String unitId;
}
