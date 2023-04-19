package org.prebid.server.bidder.huaweiads.model.response;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Content {

    String contentId;

    Integer interactionType;

    Integer creativeType;

    MetaData metaData;

    List<Monitor> monitorList;

    String cur;

    Double price;

}
