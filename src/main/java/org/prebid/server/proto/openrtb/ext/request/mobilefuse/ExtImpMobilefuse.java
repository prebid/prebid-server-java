package org.prebid.server.proto.openrtb.ext.request.mobilefuse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.mobilefuse
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpMobilefuse {

    @JsonProperty("placement_id")
    Integer placementId;

    @JsonProperty("pub_id")
    Integer publisherId;

    @JsonProperty("tagid_src")
    String tagidSrc;
}
