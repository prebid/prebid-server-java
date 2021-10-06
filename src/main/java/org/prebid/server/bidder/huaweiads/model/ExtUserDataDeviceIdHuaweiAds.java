package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value(staticConstructor = "of")
public class ExtUserDataDeviceIdHuaweiAds {

    List<String> imei;

    List<String> oaid;

    List<String> gaid;

    List<String> clientTime;
}

