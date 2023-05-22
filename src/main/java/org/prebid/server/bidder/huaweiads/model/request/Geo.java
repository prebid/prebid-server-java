package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Value;

import java.math.BigDecimal;

@Value(staticConstructor = "of")
public class Geo {

    BigDecimal lon;

    BigDecimal lat;

    Integer accuracy;

    Integer lastfix;
}
