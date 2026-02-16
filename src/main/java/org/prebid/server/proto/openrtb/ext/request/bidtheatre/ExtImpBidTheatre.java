package org.prebid.server.proto.openrtb.ext.request.bidtheatre;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBidTheatre {

    @JsonProperty("publisherId")
    String publisherId;

}
