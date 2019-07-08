package org.prebid.server.bidder.sharethrough.model.bidResponse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class ExtImpSharethroughCreativeBeacons {

    List<String> click;

    List<String> impression;

    List<String> play;

    List<String> visible;

    @JsonProperty("win-notification")
    List<String> winNotification;
}

