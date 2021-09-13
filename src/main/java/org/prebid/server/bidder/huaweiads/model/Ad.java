package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Ad {
    private Integer adType;
    private String slotId;
    private Integer retcode;
    private List<Content> content;
}
