package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtUserDataHuaweiAds {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    ExtUserDataDeviceIdHuaweiAds data;
}
