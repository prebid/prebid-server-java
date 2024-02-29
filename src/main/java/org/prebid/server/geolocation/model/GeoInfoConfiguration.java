package org.prebid.server.geolocation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class GeoInfoConfiguration {

    @JsonProperty("address-pattern")
    String addressPattern;

    GeoInfo geoInfo;
}
