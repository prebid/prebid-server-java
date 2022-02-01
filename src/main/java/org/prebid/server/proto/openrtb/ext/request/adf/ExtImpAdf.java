package org.prebid.server.proto.openrtb.ext.request.adf;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdf {

    String mid;

    Integer inv;

    String mname;

    @JsonProperty("priceType")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    String priceType;
}
