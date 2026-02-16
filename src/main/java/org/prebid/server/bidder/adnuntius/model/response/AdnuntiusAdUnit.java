package org.prebid.server.bidder.adnuntius.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidder.adnuntius.model.request.AdnuntiusNativeRequest;

import java.util.List;

@Builder
@Value
public class AdnuntiusAdUnit {

    @JsonProperty("auId")
    String auId;

    @JsonProperty("targetId")
    String targetId;

    String html;

    @JsonProperty("matchedAdCount")
    Integer matchedAdCount;

    @JsonProperty("responseId")
    String responseId;

    @JsonProperty("nativeJson")
    AdnuntiusNativeRequest nativeJson;

    List<AdnuntiusAd> ads;

    List<AdnuntiusAd> deals;
}
