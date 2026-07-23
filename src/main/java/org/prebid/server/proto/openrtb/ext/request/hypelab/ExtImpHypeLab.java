package org.prebid.server.proto.openrtb.ext.request.hypelab;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpHypeLab {

    @JsonProperty("property_slug")
    String propertySlug;

    @JsonProperty("placement_slug")
    String placementSlug;
}
