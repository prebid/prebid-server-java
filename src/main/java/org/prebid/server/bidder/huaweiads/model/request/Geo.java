package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Geo {

    @JsonProperty("lon")
    Float lon;

    @JsonProperty("lat")
    Float lat;

    @JsonProperty("accuracy")
    Integer accuracy;

    @JsonProperty("lastfix")
    Integer lastfix;
}
