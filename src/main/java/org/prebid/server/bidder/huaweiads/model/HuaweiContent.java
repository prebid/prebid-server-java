package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Builder
@Value
public class HuaweiContent {

    String contentid;

    Integer interactiontype;

    Integer creativetype;

    HuaweiMetadata metaData;

    List<HuaweiMonitor> monitor;

    String cur;

    BigDecimal price;
}

