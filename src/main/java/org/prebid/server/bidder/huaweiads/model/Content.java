package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class Content {
    private String contentid;
    private Integer interactiontype;
    private Integer creativetype;
    private Metadata metaData;
    private List<Monitor> monitor;
    private String cur;
    private BigDecimal price;
}
