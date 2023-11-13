package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtUserDataDeviceIdHuaweiAds {

    @JsonProperty("imei")
    List<String> imei;

    @JsonProperty("oaid")
    List<String> oaid;

    @JsonProperty("gaid")
    List<String> gaid;

    @JsonProperty("clientTime")
    List<String> clientTime;
}
