package org.prebid.server.bidder.logan.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class LoganRequestImpExt {

    @JsonProperty("placementId")
    String placementId;

    String type;
}
