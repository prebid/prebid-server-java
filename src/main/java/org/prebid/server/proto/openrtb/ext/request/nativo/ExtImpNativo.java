package org.prebid.server.proto.openrtb.ext.request.nativo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.nativo
 */
@Value(staticConstructor = "of")
public class ExtImpNativo {

    @JsonProperty("placementId")
    Object placementId;
}
