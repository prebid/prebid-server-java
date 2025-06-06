package org.prebid.server.bidder.adnuntius.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class AdnuntiusRequestAdUnit {

    @JsonProperty("auId")
    String auId;

    @JsonProperty("targetId")
    String targetId;

    List<List<Integer>> dimensions;

    @JsonProperty("maxDeals")
    Integer maxDeals;

    @JsonProperty("nativeRequest")
    AdnuntiusNativeRequest nativeRequest;

    @JsonProperty("adType")
    String adType;
}
