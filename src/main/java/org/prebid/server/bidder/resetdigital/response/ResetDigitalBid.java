package org.prebid.server.bidder.resetdigital.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ResetDigitalBid {

    String bidId;

    String impId;

    BigDecimal cpm;

    String cid;

    String crid;

    String adid;

    String w;

    String h;

    String seat;

    String html;
}
