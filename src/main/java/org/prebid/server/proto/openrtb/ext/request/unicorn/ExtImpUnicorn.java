package org.prebid.server.proto.openrtb.ext.request.unicorn;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class ExtImpUnicorn {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("mediaId")
    String mediaId;

    @JsonProperty("accountId")
    Integer accountId;
}
