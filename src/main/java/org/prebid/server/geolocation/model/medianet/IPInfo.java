package org.prebid.server.geolocation.model.medianet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class IPInfo {

    @JsonProperty("cc")
    String countryCode;

    @JsonProperty("stateCode")
    String stateCode;

    @JsonProperty("city")
    String city;
}
