package org.prebid.server.proto.openrtb.ext.request.feedad;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpFeedAd {

    @JsonProperty("clientToken")
    String clientToken;

    @JsonProperty("decoration")
    String decoration;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("sdkOptions")
    ExtImpFeedAdSdkOptions sdkOptions;
}
