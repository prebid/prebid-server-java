package org.prebid.server.proto.openrtb.ext.request.rhythmone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.rhythmone
 */
@Builder(toBuilder = true)
@Value
public class ExtImpRhythmone {

    @JsonProperty("placementId")
    String placementId;

    String zone;

    String path;

    @JsonProperty("S2S")
    Boolean s2s;
}
