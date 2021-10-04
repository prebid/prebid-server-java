package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class HuaweiContent {

    String contentid;

    int interactiontype;

    int creativetype;

    HuaweiMetadata metaData;

    List<HuaweiMonitor> monitor;

    String cur;

    BigDecimal price;
}
