package org.prebid.server.bidder.adform.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Builder
@Value
public class AdformBid {

    String response;

    String banner;

    BigDecimal winBid;

    String winCur;

    Integer width;

    Integer height;

    String dealId;

    String winCrid;
}
