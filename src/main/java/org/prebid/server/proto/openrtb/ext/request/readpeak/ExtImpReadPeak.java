package org.prebid.server.proto.openrtb.ext.request.readpeak;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class ExtImpReadPeak {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("siteId")
    BigDecimal siteId;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;

    @JsonProperty("tagId")
    BigDecimal tagId;
}
