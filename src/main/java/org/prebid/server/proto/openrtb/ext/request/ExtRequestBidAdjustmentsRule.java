package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.bidadjustments.model.BidAdjustmentType;

import java.math.BigDecimal;

@Builder(toBuilder = true)
@Value
public class ExtRequestBidAdjustmentsRule {

    @JsonProperty("adjtype")
    BidAdjustmentType adjType;

    BigDecimal value;

    String currency;

    public String toString() {
        return "[adjtype=%s, value=%s, currency=%s]".formatted(adjType, value, currency);
    }
}
