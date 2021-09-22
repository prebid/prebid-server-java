package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Builder
@Value
public class ExtImpHuaweiAds {
    @JsonProperty("slotId")
    String slotId;
    @JsonProperty("adType")
    String adtype;
    @JsonProperty("publisherId")
    String publisherId;
    @JsonProperty("signKey")
    String signKey;
    @JsonProperty("keyId")
    String keyId;
    @JsonProperty("isTestAuthorization")
    String isTestAuthorization;
}
