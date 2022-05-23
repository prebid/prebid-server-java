package org.prebid.server.bidder.adnuntius.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Builder
@Value
public class AdnuntiusAd {

    AdnuntiusBid bid;

    @JsonProperty("adId")
    String adId;

    @JsonProperty("creativeWidth")
    String creativeWidth;

    @JsonProperty("creativeHeight")
    String creativeHeight;

    @JsonProperty("creativeId")
    String creativeId;

    @JsonProperty("lineItemId")
    String lineItemId;

    @JsonProperty("destinationUrls")
    Map<String, String> destinationUrls;
}
