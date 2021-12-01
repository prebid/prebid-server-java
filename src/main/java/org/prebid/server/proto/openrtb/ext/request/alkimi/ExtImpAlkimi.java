package org.prebid.server.proto.openrtb.ext.request.alkimi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ExtImpAlkimi {

    @JsonProperty("placement_id")
    Integer placementId;
}
