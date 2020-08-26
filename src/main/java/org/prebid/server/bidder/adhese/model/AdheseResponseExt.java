package org.prebid.server.bidder.adhese.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class AdheseResponseExt {

    String id;

    @JsonProperty("orderId")
    String orderId;

    @JsonProperty("impressionCounter")
    String impressionCounter;

    String tag;

    String ext;
}
