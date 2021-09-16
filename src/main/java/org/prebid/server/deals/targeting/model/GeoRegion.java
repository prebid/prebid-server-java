package org.prebid.server.deals.targeting.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class GeoRegion {

    Float lat;

    Float lon;

    @JsonProperty("radiusMiles")
    Float radiusMiles;
}
