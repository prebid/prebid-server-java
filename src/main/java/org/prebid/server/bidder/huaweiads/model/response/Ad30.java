package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class Ad30 {

    Integer adType;

    String slotId;

    Integer retCode30;

    List<Content> contentList;
}
