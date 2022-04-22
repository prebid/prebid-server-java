package org.prebid.server.proto.openrtb.ext.request.adf;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ExtImpAdf {

    String mid;

    Integer inv;

    String mname;

    @JsonProperty("priceType")
    String priceType;
}
