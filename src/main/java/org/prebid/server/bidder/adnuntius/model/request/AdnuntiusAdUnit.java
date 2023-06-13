package org.prebid.server.bidder.adnuntius.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AdnuntiusAdUnit {

    @JsonProperty("auId")
    String auId;

    @JsonProperty("targetId")
    String targetId;

    List<List<Integer>> dimensions;
}
