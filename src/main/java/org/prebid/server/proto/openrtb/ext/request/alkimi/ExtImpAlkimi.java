package org.prebid.server.proto.openrtb.ext.request.alkimi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder(toBuilder = true)
public class ExtImpAlkimi {

    String token;

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;

    Integer instl;

    Integer exp;

    @JsonProperty("adUnitCode")
    String adUnitCode;
}
