package org.prebid.server.bidder.adnuntius.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class AdnuntiusAdsUnit {

    @JsonProperty("auId")
    String auId;

    @JsonProperty("targetId")
    String targetId;

    String html;

    List<AdnuntiusAd> ads;
}
