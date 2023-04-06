package org.prebid.server.proto.openrtb.ext.request.krushmedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpKrushmedia {

    @JsonProperty("key")
    String accountId;
}
