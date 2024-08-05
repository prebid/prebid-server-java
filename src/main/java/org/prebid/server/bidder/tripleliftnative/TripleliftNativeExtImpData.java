package org.prebid.server.bidder.tripleliftnative;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class TripleliftNativeExtImpData {

    @JsonProperty("tag_code")
    String tagCode;
}
