package org.prebid.server.proto.openrtb.ext.request.readpeak;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpReadPeak {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("siteId")
    String siteId;

    @JsonProperty("bidfloor")
    String bidFloor;

    @JsonProperty("tagId")
    String tagId;
}
