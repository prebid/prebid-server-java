package org.prebid.server.proto.openrtb.ext.request.readpeak;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class ExtImpReadPeak {

    @JsonProperty("publisherId")
    String publisherId;

    @JsonProperty("siteId")
    String siteId;

    @JsonProperty("bidfloor")
    BigDecimal bidFloor;

    @JsonProperty("tagId")
    String tagId;
}
