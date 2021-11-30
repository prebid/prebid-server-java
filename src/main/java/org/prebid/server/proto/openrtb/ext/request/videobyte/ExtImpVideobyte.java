package org.prebid.server.proto.openrtb.ext.request.videobyte;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpVideobyte {

    @JsonProperty("pubId")
    String publisherId;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("nid")
    String networkId;
}
