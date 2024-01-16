package org.prebid.server.proto.openrtb.ext.request.teads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class TeadsImpExt {

    @JsonProperty("placementId")
    Integer placementId;

}
