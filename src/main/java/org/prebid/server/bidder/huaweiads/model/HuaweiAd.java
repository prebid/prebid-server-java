package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class HuaweiAd {

    Integer adType;

    String slotId;

    Integer retcode;

    List<HuaweiContent> content;
}

