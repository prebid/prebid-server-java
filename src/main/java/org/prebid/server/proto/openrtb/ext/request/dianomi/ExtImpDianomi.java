package org.prebid.server.proto.openrtb.ext.request.dianomi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ExtImpDianomi {

    @JsonProperty("smartadId")
    String smartAdId;

    @JsonProperty("priceType")
    String priceType;
}
