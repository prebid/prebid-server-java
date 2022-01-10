package org.prebid.server.bidder.stroeercore.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class StroeercoreBid {

    @JsonProperty("id")
    String id;
    @JsonProperty("bidId")
    String bidId;
    @JsonProperty("cpm")
    BigDecimal cpm;
    @JsonProperty("width")
    int width;
    @JsonProperty("height")
    int height;
    @JsonProperty("ad")
    String adMarkup;
    @JsonProperty("crid")
    String creativeId;
}

