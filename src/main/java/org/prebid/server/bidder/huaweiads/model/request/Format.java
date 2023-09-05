package org.prebid.server.bidder.huaweiads.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class Format {

    @JsonProperty("w")
    Integer w;

    @JsonProperty("h")
    Integer h;

}
