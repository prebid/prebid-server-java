package org.prebid.server.bidder.adgeneration.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import lombok.Value;

@Value(staticConstructor = "of")
public class AdgenerationResponse {

    @JsonProperty("locationid")
    String locationId;

    @JsonProperty("location_params")
    AdgenerationLocationParams locationParams;

    List<AdgenerationResult> results;
}
