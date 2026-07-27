package org.prebid.server.bidder.adnuntius.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

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

    @JsonProperty("c")
    List<String> category;

    List<String> segments;

    List<String> keywords;

    @JsonProperty("kv")
    Map<String, List<String>> keyValues;

    @JsonProperty("auml")
    List<String> adUnitMatchingLabel;
}
