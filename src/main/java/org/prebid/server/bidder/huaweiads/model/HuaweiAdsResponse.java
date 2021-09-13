package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class HuaweiAdsResponse {
    private Integer retcode;
    private String reason;
    private List<Ad> multiad;
}
