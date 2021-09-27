package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class HuaweiMonitor {
    private String eventType;
    private List<String> url;
}
