package org.prebid.server.proto.openrtb.ext.request.afront;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAfront {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("sourceId")
    String sourceId;
}
