package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtUserDataHuaweiAds {

    ExtUserDataDeviceIdHuaweiAds data;
}
