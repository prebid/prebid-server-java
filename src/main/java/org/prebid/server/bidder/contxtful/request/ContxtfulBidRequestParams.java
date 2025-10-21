package org.prebid.server.bidder.contxtful.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ContxtfulBidRequestParams {

    @JsonProperty("placementId")
    String placementId;
}
