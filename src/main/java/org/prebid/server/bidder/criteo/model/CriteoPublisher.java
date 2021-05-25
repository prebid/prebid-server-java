package org.prebid.server.bidder.criteo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class CriteoPublisher {

    @JsonProperty("siteid")
    String siteId;

    String url;

    @JsonProperty("bundleid")
    String bundleId;

    @JsonProperty("networkid")
    Integer networkId;
}
