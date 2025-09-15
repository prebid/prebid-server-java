package org.prebid.server.analytics.reporter.liveintent.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder(toBuilder = true)
@Value
public class PbsjBid {

    String bidId;

    boolean enriched;

    BigDecimal price;

    String adUnitId;

    String currency;

    Float treatmentRate;

    Long timestamp;

    String partnerId;

}
