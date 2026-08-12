package org.prebid.server.proto.openrtb.ext.request.bidwave;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBidwave {

    @JsonProperty("publisherId")
    String publisherId;
}
