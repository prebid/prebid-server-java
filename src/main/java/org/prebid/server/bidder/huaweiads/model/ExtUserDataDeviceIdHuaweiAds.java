package org.prebid.server.bidder.huaweiads.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
@Builder
public class ExtUserDataDeviceIdHuaweiAds {

    String[] imei;
    String[] oaid;
    String[] gaid;
    String[] clientTime;
}
