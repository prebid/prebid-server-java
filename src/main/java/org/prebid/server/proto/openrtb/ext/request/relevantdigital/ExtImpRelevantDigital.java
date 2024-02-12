package org.prebid.server.proto.openrtb.ext.request.relevantdigital;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value(staticConstructor = "of")
@Builder
public class ExtImpRelevantDigital {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("pbsHost")
    String pbsHost;

    @JsonProperty("pbsBufferMs")
    Long pbsBufferMs;

}
