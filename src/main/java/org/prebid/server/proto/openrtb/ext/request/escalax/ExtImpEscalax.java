package org.prebid.server.proto.openrtb.ext.request.escalax;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpEscalax {

    @JsonProperty("sourceId")
    String sourceId;

    @JsonProperty("accountId")
    String accountId;
}
