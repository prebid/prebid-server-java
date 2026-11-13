package org.prebid.server.bidder.adgeneration.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AdgenerationResponse {

    @JsonProperty("locationid")
    String locationId;

    @JsonProperty("location_params")
    AdgenerationLocationParams locationParams;

    List<AdgenerationResult> results;
}
