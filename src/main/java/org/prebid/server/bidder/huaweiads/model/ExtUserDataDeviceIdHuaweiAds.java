package org.prebid.server.bidder.huaweiads.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtUserDataDeviceIdHuaweiAds {
    String[] imei;
    String[] oaid;
    String[] gaid;
    String[] clientTime;
}
