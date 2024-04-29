package org.prebid.server.bidder.huaweiads.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Monitor {

    @JsonProperty("eventType")
    String eventType;

    @JsonProperty("url")
    List<String> urlList;
}
