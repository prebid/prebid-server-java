package org.prebid.server.proto.openrtb.ext.request.admatic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AdmaticImpExt {

    @JsonProperty("host")
    String host;

    @JsonProperty("networkId")
    Integer networkId;
}
