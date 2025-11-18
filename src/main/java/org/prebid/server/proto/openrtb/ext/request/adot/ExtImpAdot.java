package org.prebid.server.proto.openrtb.ext.request.adot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.adot
 */
@Value(staticConstructor = "of")
public class ExtImpAdot {

    Boolean parallax;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("publisherPath")
    String publisherPath;
}
