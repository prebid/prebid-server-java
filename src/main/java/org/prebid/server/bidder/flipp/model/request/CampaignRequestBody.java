package org.prebid.server.bidder.flipp.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder(toBuilder = true)
@Getter
public class CampaignRequestBody {

    String ip;

    List<String> keywords;

    List<Placement> placements;

    @JsonProperty("preferred_language")
    String preferredLanguage;

    String url;

    CampaignRequestBodyUser user;
}
