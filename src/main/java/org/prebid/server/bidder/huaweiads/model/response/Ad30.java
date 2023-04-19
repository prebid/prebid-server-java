package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Ad30 {

    Integer adType;

    String slotId;

    Integer retCode30;

    List<Content> contentList;
}
