package org.prebid.server.proto.openrtb.ext.request.nobid;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpNobid {

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("placementId")
    Integer placementId;
}
