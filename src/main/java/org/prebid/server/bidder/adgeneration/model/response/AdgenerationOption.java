package org.prebid.server.bidder.adgeneration.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AdgenerationOption {

    @JsonProperty("ad_type")
    String adType;
}
