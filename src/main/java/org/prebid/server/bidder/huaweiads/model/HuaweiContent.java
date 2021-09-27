package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class HuaweiContent {
    private String contentid;
    private Integer interactiontype;
    private Integer creativetype;
    private HuaweiMetadata metaData;
    private List<HuaweiMonitor> monitor;
    private String cur;
    private BigDecimal price;
}
