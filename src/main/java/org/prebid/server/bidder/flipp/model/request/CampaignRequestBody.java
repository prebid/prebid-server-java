package org.prebid.server.bidder.flipp.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class CampaignRequestBody {

    @JsonProperty("ip")
    String ip;

    @JsonProperty("keywords")
    List<String> keywords;

    @JsonProperty("placements")
    List<Placement> placements;

    String preferredLanguage;

    @JsonProperty("url")
    String url;

    @JsonProperty("user")
    CampaignRequestBodyUser user;
}
