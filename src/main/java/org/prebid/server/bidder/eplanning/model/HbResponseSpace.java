package org.prebid.server.bidder.eplanning.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class HbResponseSpace {

    @JsonProperty("k")
    String name;

    @JsonProperty("a")
    List<HbResponseAd> ads;
}
