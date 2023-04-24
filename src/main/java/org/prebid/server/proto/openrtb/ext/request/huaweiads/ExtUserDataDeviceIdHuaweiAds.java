package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtUserDataDeviceIdHuaweiAds {

    String[] imei;

    String[] oaid;

    String[] gaid;

    String[] clientTime;

}
