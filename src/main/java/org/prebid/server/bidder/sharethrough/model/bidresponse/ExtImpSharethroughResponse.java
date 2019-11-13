package org.prebid.server.bidder.sharethrough.model.bidresponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ExtImpSharethroughResponse {

    @JsonProperty("adserverRequestId")
    String adserverRequestId;

    @JsonProperty("bidId")
    String bidId;

    List<ExtImpSharethroughCreative> creatives;
}

