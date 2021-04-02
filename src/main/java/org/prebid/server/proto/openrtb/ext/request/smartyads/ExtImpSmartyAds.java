package org.prebid.server.proto.openrtb.ext.request.smartyads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpSmartyAds {

    @JsonProperty("accountid")
    String accountId;

    @JsonProperty("sourceid")
    String sourceId;

    String host;
}
