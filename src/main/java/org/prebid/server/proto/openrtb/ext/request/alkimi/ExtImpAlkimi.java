package org.prebid.server.proto.openrtb.ext.request.alkimi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

@Value
public class ExtImpAlkimi {

    String token;

    @JsonProperty("bidFloor")
    BigDecimal bidFloor;

    Integer pos;
}
