package org.prebid.server.proto.openrtb.ext.request.adnuntius;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class ExtImpAdnuntius {

    @JsonProperty("auId")
    String auId;

    String network;

    @JsonProperty("noCookies")
    Boolean noCookies;

    @JsonProperty("maxDeals")
    Integer maxDeals;

    @JsonProperty("bidType")
    String bidType;
}
