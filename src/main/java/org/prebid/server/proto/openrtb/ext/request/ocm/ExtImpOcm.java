package org.prebid.server.proto.openrtb.ext.request.ocm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpOcm {

    /**
     * Orange Click Media publisher identifier. It is propagated to the request-level publisher id,
     * so every impression in a single request must carry the same value.
     */
    @JsonProperty("publisherId")
    String publisherId;

    /**
     * Names the impression's stored request on the OCM endpoint, which expands it into the
     * impression's real configuration.
     */
    @JsonProperty("placementId")
    String placementId;
}
