package org.prebid.server.proto.openrtb.ext.request.openweb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOpenweb {

    Integer aid;

    String org;

    @JsonProperty("placementId")
    String placementId;
}
