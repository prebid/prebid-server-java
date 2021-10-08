package org.prebid.server.bidder.huaweiads.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class HuaweiAdSlot {

    String slotId;

    Integer adType;

    Integer test;

    Integer totalDuration;

    Integer orientation;

    Integer w;

    Integer h;

    List<HuaweiFormat> format;

    List<String> detailedCreativeTypeList;
}

