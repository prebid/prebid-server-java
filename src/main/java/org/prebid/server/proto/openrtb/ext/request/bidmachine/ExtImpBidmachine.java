package org.prebid.server.proto.openrtb.ext.request.bidmachine;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpBidmachine {
    String host;

    String path;

    @JsonProperty("seller_id")
    String sellerId;
}
