package org.prebid.server.proto.openrtb.ext.request.gothamads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class GothamAdsImpExt {

    @JsonProperty("accountId")
    String accountId;

}
