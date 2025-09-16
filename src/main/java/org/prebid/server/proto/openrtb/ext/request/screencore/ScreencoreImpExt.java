package org.prebid.server.proto.openrtb.ext.request.screencore;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ScreencoreImpExt {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("placementId")
    String placementId;
}
