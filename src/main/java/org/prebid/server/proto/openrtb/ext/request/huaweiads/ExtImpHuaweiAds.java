package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpHuaweiAds {
    @JsonProperty("slotid")
    String slotId;
    @JsonProperty("adtype")
    String adtype;
    @JsonProperty("publisherid")
    String publisherId;
    @JsonProperty("signkey")
    String signKey;
    @JsonProperty("keyid")
    String keyId;
}
