package org.prebid.server.proto.openrtb.ext.request.huaweiads;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpHuaweiAds {

    String slotId;

    String adtype;

    String publisherId;

    String signKey;

    String keyId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String isTestAuthorization;
}
