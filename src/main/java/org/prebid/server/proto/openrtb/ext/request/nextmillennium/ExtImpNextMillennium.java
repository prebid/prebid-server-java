package org.prebid.server.proto.openrtb.ext.request.nextmillennium;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ExtImpNextMillennium {

    String placementId;

    String groupId;

    @JsonProperty("adSlots")
    List<String> adSlots;

    @JsonProperty("allowedAds")
    List<String> allowedAds;
}
