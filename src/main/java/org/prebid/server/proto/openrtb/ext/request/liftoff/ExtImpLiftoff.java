package org.prebid.server.proto.openrtb.ext.request.liftoff;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
public class ExtImpLiftoff {

    @JsonProperty("bid_token")
    String bidToken;

    @JsonProperty("app_store_id")
    String appStoreId;

    @JsonProperty("placement_reference_id")
    String placementReferenceId;
}
