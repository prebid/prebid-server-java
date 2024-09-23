package org.prebid.server.proto.openrtb.ext.request.blasto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBlasto {

    @JsonProperty("host")
    String host;

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("sourceId")
    String sourceId;
}
