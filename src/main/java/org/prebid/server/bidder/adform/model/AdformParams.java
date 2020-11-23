package org.prebid.server.bidder.adform.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AdformParams {

    Long mid;

    @JsonProperty("priceType")
    String priceType;

    @JsonProperty("mkv")
    String keyValues;

    @JsonProperty("mkw")
    String keyWords;

    String cdims;

    @JsonProperty("minp")
    Double minPrice;

    String url;
}
