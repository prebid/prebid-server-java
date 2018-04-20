package org.prebid.server.bidder.index.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class IndexParams {

    @JsonProperty("siteID")
    Integer siteId;
}
