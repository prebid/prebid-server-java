package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtUserDataDeviceIdHuaweiAds {

    List<String> imei;

    List<String> oaid;

    List<String> gaid;

    @JsonProperty("clientTime")
    List<String> clientTime;
}
