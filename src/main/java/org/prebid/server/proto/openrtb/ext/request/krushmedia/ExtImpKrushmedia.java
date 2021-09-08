package org.prebid.server.proto.openrtb.ext.request.krushmedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpKrushmedia {

    @JsonProperty("key")
    String accountId;
}
