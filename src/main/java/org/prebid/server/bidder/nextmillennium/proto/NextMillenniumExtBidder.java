package org.prebid.server.bidder.nextmillennium.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class NextMillenniumExtBidder {

    @JsonProperty("nmmFlags")
    List<String> nmmFlags;

    @JsonProperty("adSlots")
    List<String> adSlots;

    @JsonProperty("allowedAds")
    List<String> allowedAds;

    String nmVersion;

    String serverVersion;

    public static NextMillenniumExtBidder of(List<String> nmmFlags) {
        return of(nmmFlags, null, null, null, null);
    }
}
