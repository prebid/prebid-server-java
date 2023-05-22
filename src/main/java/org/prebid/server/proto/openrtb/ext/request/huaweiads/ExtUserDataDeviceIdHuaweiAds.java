package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtUserDataDeviceIdHuaweiAds {

    List<String> imei;

    List<String> oaid;

    List<String> gaid;

    List<String> clientTime;
}
