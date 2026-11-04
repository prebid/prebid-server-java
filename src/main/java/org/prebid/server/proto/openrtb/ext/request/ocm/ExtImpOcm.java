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

    /**
     * Deducted from the request tmax before it is forwarded. The OCM endpoint is itself a Prebid Server
     * that would otherwise spend the whole budget on its own bidders, leaving no room for the round trip
     * back. Null selects the adapter default; an explicit 0 disables the buffer.
     */
    @JsonProperty("tmaxBufferMs")
    Integer tmaxBufferMs;
}
