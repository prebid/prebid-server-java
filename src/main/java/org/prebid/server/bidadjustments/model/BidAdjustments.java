package org.prebid.server.bidadjustments.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class BidAdjustments {

    @JsonProperty("mediatype")
    Map<String, Map<String, Map<String, List<BidAdjustmentsRule>>>> rules;

}
