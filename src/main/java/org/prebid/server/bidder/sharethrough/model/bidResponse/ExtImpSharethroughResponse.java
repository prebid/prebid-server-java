package org.prebid.server.bidder.sharethrough.model.bidResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ExtImpSharethroughResponse {
    //Be aware that in prod we change name to snake_case so we need specify value explicitly

    @JsonProperty("adserverRequestId")
    String adserverRequestId;

    @JsonProperty("bidId")
    String bidId;

    @JsonProperty("cookieSyncUrls")
    List<String> cookieSyncUrls;

    List<ExtImpSharethroughCreative> creatives;

    ExtImpSharethroughPlacement placement;

    @JsonProperty("stxUserId")
    String stxUserId;
}
