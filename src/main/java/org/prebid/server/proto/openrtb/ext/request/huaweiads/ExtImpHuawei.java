package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import lombok.Builder;
import lombok.Value;

@Builder
@Value(staticConstructor = "of")
public class ExtImpHuawei {

    String slotId;

    String adType;

    String publisherId;

    String signKey;

    String keyId;

    String isTestAuthorization;
}

