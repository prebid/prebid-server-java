package org.prebid.server.bidder.eplanning.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class HbResponseAd {

    @JsonProperty("i")
    String impressionId;

    @JsonProperty("id")
    String adId;

    @JsonProperty("pr")
    String price;

    @JsonProperty("adm")
    String adM;

    @JsonProperty("crid")
    String crId;

    @JsonProperty("w")
    Integer width;

    @JsonProperty("h")
    Integer height;
}
