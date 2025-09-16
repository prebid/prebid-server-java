package org.prebid.server.proto.openrtb.ext.request.smartyads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSmartyAds {

    @JsonProperty("accountid")
    String accountId;

    @JsonProperty("sourceid")
    String sourceId;

    String host;
}
