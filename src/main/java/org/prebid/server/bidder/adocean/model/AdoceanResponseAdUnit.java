package org.prebid.server.bidder.adocean.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AdoceanResponseAdUnit {

    String id;

    String crid;

    String currency;

    String price;

    String width;

    String height;

    String code;

    @JsonProperty("winurl")
    String winUrl;

    @JsonProperty("statsUrl")
    String statsUrl;

    String error;
}
