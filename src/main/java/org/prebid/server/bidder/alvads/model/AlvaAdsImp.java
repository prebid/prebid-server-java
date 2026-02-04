package org.prebid.server.bidder.alvads.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
@Builder
public class AlvaAdsImp {

    String id;

    Map<String, Object> banner;

    Map<String, Object> video;

    String tagid;

    BigDecimal bidfloor;
}
