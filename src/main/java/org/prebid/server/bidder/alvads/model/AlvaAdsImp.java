package org.prebid.server.bidder.alvads.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class AlvaAdsImp {

    private String id;
    private Map<String, Object> banner;
    private Map<String, Object> video;
    private String tagid;
    private BigDecimal bidfloor;
}
