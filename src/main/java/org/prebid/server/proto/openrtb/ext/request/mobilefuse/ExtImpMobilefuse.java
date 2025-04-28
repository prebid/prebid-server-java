package org.prebid.server.proto.openrtb.ext.request.mobilefuse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.mobilefuse
 */
@Value(staticConstructor = "of")
public class ExtImpMobilefuse {

    @JsonProperty("placement_id")
    Integer placementId;

    @JsonProperty("pub_id")
    Integer publisherId;

    @JsonProperty("tagid_src")
    String tagidSrc;
}
