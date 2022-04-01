package org.prebid.server.proto.openrtb.ext.request.adot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adot
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAdot {

    Boolean parallax;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("publisherPath")
    String publisherPath;
}
