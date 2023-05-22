package org.prebid.server.bidder.huaweiads.model.request;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class AdSlot30 {

    String slotid;

    Integer adtype;

    Integer test;

    Integer totalDuration;

    Integer orientation;

    Integer w;

    Integer h;

    List<Format> format;

    List<String> detailedCreativeTypeList;
}
