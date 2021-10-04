package org.prebid.server.bidder.huaweiads.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtUserDataHuaweiAds {

    ExtUserDataDeviceIdHuaweiAds data;
}
