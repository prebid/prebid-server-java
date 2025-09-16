package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class ExtImpHuaweiAds {

    @JsonProperty("slotid")
    String slotId;

    @JsonProperty("adtype")
    String adType;

    @JsonProperty("publisherid")
    String publisherId;

    @JsonProperty("signkey")
    String signKey;

    @JsonProperty("keyid")
    String keyId;

    @JsonProperty("isTestAuthorization")
    String isTestAuthorization;
}
