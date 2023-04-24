package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpHuaweiAds {

    String slotId;

    String adtype;

    String publisherId;

    String signKey;

    String keyId;

    String isTestAuthorization;
}
