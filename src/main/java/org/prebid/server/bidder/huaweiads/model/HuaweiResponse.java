package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class HuaweiResponse {

    Integer retcode;

    String reason;

    List<HuaweiAd> multiad;
}

