package org.prebid.server.proto.openrtb.ext.request.bidstack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBidstack {

    @JsonProperty("publisherId")
    String publisherId;
}
