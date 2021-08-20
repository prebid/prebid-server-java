package org.prebid.server.bidder.smarthub.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class SmarthubImpExt {

    String partnerName;
    String seat;
    String token;
}


