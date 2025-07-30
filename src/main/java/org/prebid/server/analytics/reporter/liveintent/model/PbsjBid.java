package org.prebid.server.analytics.reporter.liveintent.model;

import lombok.Builder;
import lombok.Data;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Optional;

@Data
@Builder(toBuilder = true)
@Value
public class PbsjBid {

    String bidId;
    boolean enriched;
    BigDecimal price;
    String adUnitId;
    String currency;
    Optional<Float> treatmentRate;
    Long timestamp;
    String partnerId;
}
