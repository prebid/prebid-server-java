package org.prebid.server.bidder.flipp.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class Inline {

    @JsonProperty("adId")
    int adId;

    @JsonProperty("advertiserId")
    Integer advertiserId;

    @JsonProperty("campaignId")
    Integer campaignId;

    @JsonProperty("clickUrl")
    String clickUrl;

    @JsonProperty("contents")
    List<Content> contents;

    @JsonProperty("creativeId")
    int creativeId;

    @JsonProperty("flightId")
    Integer flightId;

    @JsonProperty("height")
    Integer height;

    @JsonProperty("impressionUrl")
    String impressionUrl;

    @JsonProperty("prebid")
    Prebid prebid;

    @JsonProperty("priorityId")
    Integer priorityId;

    @JsonProperty("width")
    Integer width;
}
