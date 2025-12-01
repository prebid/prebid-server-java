package org.prebid.server.bidder.goldbach.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ExtImpGoldbachBidRequest {

    @JsonProperty("slotId")
    String slotId;

    Map<String, List<String>> targetings;
}
