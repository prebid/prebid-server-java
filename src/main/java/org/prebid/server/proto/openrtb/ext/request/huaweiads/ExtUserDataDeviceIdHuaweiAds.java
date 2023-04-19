package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtUserDataDeviceIdHuaweiAds {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String[] imei;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String[] oaid;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String[] gaid;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String[] clientTime;

}
