package org.prebid.server.proto.openrtb.ext.request.unicorn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
@Builder(toBuilder = true)
public class ExtImpUnicorn {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("publisherId")
    Integer publisherId;

    @JsonProperty("mediaId")
    String mediaId;

    @JsonProperty("accountId")
    Integer accountId;
}
