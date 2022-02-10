package org.prebid.server.proto.openrtb.ext.request.videobyte;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value(staticConstructor = "of")
public class ExtImpVideobyte {

    @JsonProperty("pubId")
    String publisherId;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("nid")
    String networkId;
}
