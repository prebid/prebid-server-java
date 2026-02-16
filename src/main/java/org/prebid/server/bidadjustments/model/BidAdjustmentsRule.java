package org.prebid.server.bidadjustments.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder(toBuilder = true)
@Value
public class BidAdjustmentsRule {

    @JsonProperty("adjtype")
    BidAdjustmentType adjType;

    BigDecimal value;

    String currency;

    public String toString() {
        return "[adjtype=%s, value=%s, currency=%s]".formatted(adjType, value, currency);
    }
}
