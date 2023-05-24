package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpHuaweiAds {

    @JsonProperty("slotid")
    String slotId;

    String adtype;

    @JsonProperty("publisherid")
    String publisherId;

    @JsonProperty("signkey")
    String signKey;

    @JsonProperty("keyid")
    String keyId;

    @JsonProperty("isTestAuthorization")
    String isTestAuthorization;
}
