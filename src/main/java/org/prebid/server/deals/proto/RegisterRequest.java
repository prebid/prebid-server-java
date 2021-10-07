package org.prebid.server.deals.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

@AllArgsConstructor(staticName = "of")
@Value
public class RegisterRequest {

    @JsonProperty("healthIndex")
    BigDecimal healthIndex;

    Status status;

    @JsonProperty("hostInstanceId")
    String hostInstanceId;

    String region;

    String vendor;
}
